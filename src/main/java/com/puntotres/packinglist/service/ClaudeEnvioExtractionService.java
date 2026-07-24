package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.model.EnvioInput;

/**
 * Modo CLAUDE de la pantalla de entrada: manda las fotos del packing list
 * a la API de Anthropic y convierte la respuesta en el mismo
 * {@link EnvioInput} que produce el JSON pegado a mano, de forma que el
 * resto del pipeline (importar → asignar → inferir) no distingue el origen.
 *
 * El cliente HTTP se crea perezosamente en el primer uso: así la aplicación
 * arranca (y los otros modos funcionan) aunque no esté configurada la
 * variable de entorno ANTHROPIC_API_KEY.
 */
@Service
public class ClaudeEnvioExtractionService {

    /** Una imagen subida: su media type (image/jpeg...) y sus bytes. */
    public record Imagen(String mediaType, byte[] datos) {
    }

    /** Fallo de extracción con mensaje pensado para mostrarse al usuario. */
    public static class ExtraccionException extends RuntimeException {
        public ExtraccionException(String mensaje) { super(mensaje); }
        public ExtraccionException(String mensaje, Throwable causa) { super(mensaje, causa); }
    }

    private static final String PROMPT_SISTEMA = """
            Eres un asistente que transcribe packing lists de un proveedor de marroquinería \
            a partir de fotos o capturas. Devuelves EXCLUSIVAMENTE un JSON válido, sin \
            markdown, sin comentarios y sin texto antes o después.

            Estructura exacta del JSON:
            {
              "cliente": "<nombre del cliente si aparece en las imágenes, si no omítelo>",
              "destinos": [
                {
                  "destino": "<nombre de la destinación, p. ej. PARIS>",
                  "palets": [
                    { "palet": 1, "cajaInicio": 1, "cajaFin": 5 }
                  ],
                  "referencias": [
                    {
                      "referencia": "<código, p. ej. USL728.AL217>",
                      "color": "<color tal cual aparece>",
                      "medidaCaja": "<LxWxH en cm, p. ej. 60x40x40>",
                      "pedido": "<número de pedido>",
                      "talla": "<solo si el artículo tiene talla, p. ej. cinturones>",
                      "cantidadTotal": 150,
                      "cajas": [
                        { "cajaInicio": 1, "cajaFin": 3, "unidadesPorCaja": 50, "pesoBruto": 18.5 },
                        { "caja": 4, "unidades": 45, "pesoBruto": 16.2 }
                      ]
                    }
                  ]
                }
              ]
            }

            Reglas:
            - Cada entrada de "cajas" tiene UNA de las dos formas: caja suelta \
            {"caja": N, "unidades": U} o rango {"cajaInicio": A, "cajaFin": B, \
            "unidadesPorCaja": U}. Un rango implica que TODAS esas cajas llevan las \
            mismas unidades; si no, usa cajas sueltas.
            - Una referencia con varias tallas o colores aparece como varias entradas \
            de "referencias", una por combinación, cada una con su cantidadTotal.
            - Una caja puede aparecer en varias referencias (caja mixta).
            - "pesoBruto" (kg) es OPCIONAL: ponlo solo si la imagen indica el peso \
            bruto de la caja. Es el peso de la caja física, así que va una sola vez \
            por caja: en un rango, el peso de cada una de sus cajas; en una caja \
            mixta (mismo nº de caja en varias entradas), solo en la primera entrada. \
            Si la imagen no da pesos, omite "pesoBruto" (no lo inventes: el peso se \
            calcula después). Transcribe el número tal cual, sin la unidad.
            - "palets" solo si las imágenes indican el reparto de cajas por palet; \
            si no aparece, deja la lista vacía.
            - Los campos opcionales ("talla", "modelo", "canal", "livraisonCode", \
            "cliente") se omiten cuando no aparecen en las imágenes.
            - Copia referencias, colores y pedidos EXACTAMENTE como aparecen, \
            respetando puntos, ceros a la izquierda y mayúsculas.
            - Verifica que la suma de unidades de "cajas" cuadra con "cantidadTotal" \
            de cada referencia; si en la imagen no cuadran, transcribe lo que ves.
            """;

    private final ObjectMapper mapper;
    private volatile AnthropicClient cliente;

    public ClaudeEnvioExtractionService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Extrae el envío de las imágenes. Lanza {@link ExtraccionException} con
     * un mensaje legible si falta la clave de API, la llamada falla o la
     * respuesta no es un JSON interpretable.
     */
    public EnvioInput extraer(List<Imagen> imagenes) {
        List<ContentBlockParam> bloques = new ArrayList<>();
        for (int i = 0; i < imagenes.size(); i++) {
            Imagen imagen = imagenes.get(i);
            bloques.add(ContentBlockParam.ofText(TextBlockParam.builder()
                    .text("Imagen " + (i + 1) + ":").build()));
            bloques.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .mediaType(mediaTypeDe(imagen.mediaType()))
                            .data(Base64.getEncoder().encodeToString(imagen.datos()))
                            .build())
                    .build()));
        }
        bloques.add(ContentBlockParam.ofText(TextBlockParam.builder()
                .text("Transcribe el packing list de estas imágenes al JSON descrito. "
                        + "Si hay varias imágenes, son páginas del mismo envío.")
                .build()));

        Message respuesta;
        try {
            respuesta = clienteApi().messages().create(MessageCreateParams.builder()
                    .model(Model.CLAUDE_OPUS_4_8)
                    .maxTokens(16000L)
                    .thinking(ThinkingConfigAdaptive.builder().build())
                    .system(PROMPT_SISTEMA)
                    .addUserMessageOfBlockParams(bloques)
                    .build());
        } catch (AnthropicServiceException e) {
            throw new ExtraccionException("La API de Claude ha devuelto un error: "
                    + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new ExtraccionException("No se pudo conectar con la API de Claude: "
                    + e.getMessage(), e);
        }

        StopReason motivoParada = respuesta.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(motivoParada)) {
            throw new ExtraccionException("Claude ha rechazado procesar las imágenes");
        }
        if (StopReason.MAX_TOKENS.equals(motivoParada)) {
            throw new ExtraccionException("La respuesta de Claude se ha cortado por longitud; "
                    + "prueba con menos imágenes por envío");
        }
        return parsear(textoDe(respuesta));
    }

    private AnthropicClient clienteApi() {
        AnthropicClient actual = cliente;
        if (actual != null) {
            return actual;
        }
        synchronized (this) {
            if (cliente == null) {
                try {
                    cliente = AnthropicOkHttpClient.fromEnv();
                } catch (RuntimeException e) {
                    throw new ExtraccionException("Falta configurar la clave de la API de Claude "
                            + "(variable de entorno ANTHROPIC_API_KEY)", e);
                }
            }
            return cliente;
        }
    }

    private static Base64ImageSource.MediaType mediaTypeDe(String mediaType) {
        String tipo = mediaType == null ? "" : mediaType.toLowerCase();
        return switch (tipo) {
            case "image/jpeg", "image/jpg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
            case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
            case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
            case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
            default -> throw new ExtraccionException(
                    "Formato de imagen no soportado: " + mediaType
                            + " (usa JPEG, PNG, GIF o WebP)");
        };
    }

    private static String textoDe(Message respuesta) {
        StringBuilder texto = new StringBuilder();
        for (ContentBlock bloque : respuesta.content()) {
            bloque.text().ifPresent(t -> texto.append(t.text()));
        }
        return texto.toString();
    }

    /**
     * Aísla el objeto JSON de la respuesta (por si el modelo lo envolviera
     * en una valla markdown pese al prompt) y lo convierte a EnvioInput.
     */
    private EnvioInput parsear(String texto) {
        int inicio = texto.indexOf('{');
        int fin = texto.lastIndexOf('}');
        if (inicio < 0 || fin <= inicio) {
            throw new ExtraccionException("La respuesta de Claude no contiene un JSON");
        }
        try {
            return mapper.readValue(texto.substring(inicio, fin + 1), EnvioInput.class);
        } catch (JsonProcessingException e) {
            throw new ExtraccionException("El JSON devuelto por Claude no es válido: "
                    + e.getOriginalMessage(), e);
        }
    }
}

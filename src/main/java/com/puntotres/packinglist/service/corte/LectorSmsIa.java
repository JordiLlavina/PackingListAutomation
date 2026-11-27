package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.service.VolcadoExcel;

/**
 * Lee con Claude el pedido de SMS (muestras de venta) de un cliente que no
 * tiene lector propio: todos menos APC, que tiene {@link ApcSmsExcel}. Cada
 * cliente lo manda a su manera, en excel o en PDF, y escribir un lector por
 * cliente no compensa.
 *
 * Mismo reparto que la conversión del excel del taller: <b>Claude lee y
 * separa; no consulta el catálogo ni inventa</b>. Lo que no viene en el
 * documento llega vacío, y lo leído pasa por la pantalla de revisión antes de
 * generar nada. Además dice de quién parece el documento, para avisar si se
 * ha subido el de otro cliente (avisar, no bloquear: a veces es una marca del
 * grupo y está bien).
 */
@Service
public class LectorSmsIa {

    /** Un fallo de la lectura, con un mensaje que se puede enseñar tal cual. */
    public static class LecturaFallida extends RuntimeException {
        public LecturaFallida(String mensaje) {
            super(mensaje);
        }

        public LecturaFallida(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    static final String MODELO = "claude-opus-5-5";
    private static final long TOKENS_MAXIMOS = 64_000L;
    private static final Duration TIEMPO_MAXIMO = Duration.ofMinutes(15);
    /** Un pedido de SMS son unas decenas de filas; por encima no es un pedido de SMS. */
    static final int MAXIMO_FILAS_POR_HOJA = 400;
    static final int MAXIMO_COLUMNAS = 40;

    /**
     * El esquema de respuesta: tiene que seguir a {@link LecturaSmsIa}. Solo
     * admiten null los números y los sí/no (la API limita a 16 los campos con
     * unión de tipos); un texto que no está es la cadena vacía.
     */
    static final String ESQUEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["clienteDetectado", "esDelCliente", "motivoCliente", "esPedidoDeSms", "motivoSms",
                           "lineas", "avisos"],
              "properties": {
                "clienteDetectado": {"type": "string", "description": "La marca o cliente de quien parece el documento; \\"\\" si no lo dice"},
                "esDelCliente": {"type": ["boolean", "null"], "description": "true si es del cliente elegido, false si es de otro, null si no hay nada que lo diga"},
                "motivoCliente": {"type": "string", "description": "Qué lo dice: logo, cabecera, nombre del fichero, forma de las referencias"},
                "esPedidoDeSms": {"type": ["boolean", "null"], "description": "false si no es un pedido de muestras (un packing, una factura, otra cosa)"},
                "motivoSms": {"type": "string"},
                "lineas": {
                  "type": "array",
                  "items": {
                    "type": "object", "additionalProperties": false,
                    "required": ["hoja", "fila", "referencia", "modelo", "nombreModelo", "codigoColor",
                                 "descripcionColor", "piel", "colorPiel", "forro", "colorForro",
                                 "combinaciones", "muestras", "comentario"],
                    "properties": {
                      "hoja": {"type": "string", "description": "Nombre de la hoja del excel, o 'Página N' en un PDF"},
                      "fila": {"type": ["integer", "null"], "description": "Número de fila de Excel de la línea (B7 → 7); null en un PDF"},
                      "referencia": {"type": "string", "description": "La referencia del artículo tal como la escribe el cliente"},
                      "modelo": {"type": "string", "description": "El código del modelo solo si el documento lo da en su propia columna; si no, \\"\\""},
                      "nombreModelo": {"type": "string", "description": "El nombre comercial del bolso"},
                      "codigoColor": {"type": "string", "description": "El código del color del bolso (LZZ, 001); \\"\\" si solo hay nombre"},
                      "descripcionColor": {"type": "string", "description": "El nombre del color del bolso (NOIR, BLACK)"},
                      "piel": {"type": "string", "description": "La piel principal (el material del proveedor)"},
                      "colorPiel": {"type": "string"},
                      "forro": {"type": "string"},
                      "colorForro": {"type": "string"},
                      "combinaciones": {
                        "type": "array", "maxItems": 4,
                        "items": {"type": "object", "additionalProperties": false, "required": ["nombre", "color"],
                                  "properties": {"nombre": {"type": "string"}, "color": {"type": "string"}}}
                      },
                      "muestras": {"type": ["integer", "null"], "description": "Cuántas muestras se cortan; 0 si el documento dice que no hay; null si no se entiende"},
                      "comentario": {"type": "string", "description": "Lo que el cliente anota en la línea y es del corte; \\"\\" si nada"}
                    }
                  }
                },
                "avisos": {"type": "array", "items": {"type": "string"}}
              }
            }
            """;

    static final String PROMPT = """
            Lees el pedido de SMS (muestras de venta) que un cliente de marroquinería (bolsos, \
            pequeña marroquinería, cinturones) manda a Punto Tres, y sacas lo que necesita la \
            HOJA DE CORTE de cada muestra: qué referencia, en qué color, cuántas muestras y con \
            qué pieles. Tu trabajo es LEER Y SEPARAR. No consultes ningún catálogo, no completes \
            nada por parecido y NO INVENTES: lo que el documento no dice va vacío ("" en los \
            textos, null en los números). Lo que leas lo revisa una persona antes de cortar.

            ## La entrada

            Un excel volcado como texto (hoja a hoja, cada fila con sus celdas "B7=valor"; \
            ~~tachado~~ es que no existe, [fondo]/[letra] son colores que puso el cliente, \
            [nota: ...] un comentario de la celda) o un PDF.

            ## Qué es una línea

            Una línea es UNA referencia en UN color del bolso con muestras que cortar. Si el \
            documento pone varios colores de una referencia, es una línea por color. Si una \
            fila hereda la referencia de la de encima (celda vacía), hereda. No juntes dos \
            filas aunque sean la misma referencia y el mismo color: van por separado.

            - referencia: tal como la escribe el cliente, sin espacios.
            - modelo: solo si el documento trae el código del modelo en una columna aparte; si \
            no, "" (lo saca la aplicación de la referencia).
            - nombreModelo: el nombre comercial del bolso, si lo trae.
            - codigoColor y descripcionColor: el color del BOLSO. Si viene "LZZ NOIR", el código \
            es LZZ y la descripción NOIR; si solo hay un nombre, código "" y el nombre en la \
            descripción. No traduzcas.
            - piel: la piel principal del bolso (el material que se corta, el del proveedor) y \
            colorPiel su color. Si hay dos pieles juntas ("A + B") la primera es la principal y \
            las demás van en combinaciones, cada una con su color si el documento los separa \
            ("x / y"). Como mucho cuatro combinaciones.
            - forro y colorForro: el forro, si lo trae.
            - muestras: cuántas muestras se cortan. Un número con texto ("4 SMS") es el número. \
            Si dice que no hay ("PAS DE SMS", "no", 0) es 0. Si la celda está vacía o no se \
            entiende, null y un aviso.
            - hoja y fila: dónde está en el documento (fila de Excel, la de la dirección de sus \
            celdas; en un PDF, hoja "Página N" y fila null).

            NO son del corte y no van en ninguna línea ni campo: cremalleras, croûte, tintes, \
            logos, piezas metálicas, hilos, embalaje, precios.

            ## De quién es el documento

            Te digo el cliente elegido en la aplicación. Mira de quién parece el documento \
            (logo, cabecera, nombre del fichero, nombres de marca, forma de las referencias) y \
            rellena clienteDetectado, esDelCliente y motivoCliente. Si nada lo dice, \
            esDelCliente null: no lo adivines. Si lo subido no es un pedido de muestras (es un \
            packing list, una factura, un pedido de producción...), esPedidoDeSms false y por \
            qué en motivoSms.

            ## Avisos

            Una frase por duda de lectura, citando dónde ("hoja FEMME, fila 12", "página 2"), \
            para que una persona la encuentre. Sin coletillas y sin hablar de JSON ni de campos.""";

    private final ObjectMapper mapper;
    private volatile AnthropicClient cliente;

    public LectorSmsIa(ObjectMapper mapper) {
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Lee el documento. Lanza {@link LecturaFallida} con un mensaje legible si
     * no se puede abrir, falta la clave de API, la llamada falla o la
     * respuesta no se entiende.
     */
    public LecturaSmsIa leer(byte[] fichero, String nombreFichero, String claveCliente, String nombreCliente) {
        List<ContentBlockParam> documento;
        try {
            documento = documento(fichero, nombreFichero);
        } catch (IOException | RuntimeException e) {
            throw new LecturaFallida("No se ha podido abrir el documento: revisa que sea un excel (.xlsx) o un "
                    + "PDF (" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()) + ")", e);
        }
        Message respuesta;
        try {
            respuesta = acumular(peticionPara(documento, claveCliente, nombreCliente));
        } catch (AnthropicServiceException e) {
            throw new LecturaFallida("El servicio de IA ha devuelto un error: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new LecturaFallida("No se pudo conectar con el servicio de IA: " + e.getMessage(), e);
        }
        StopReason motivo = respuesta.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(motivo)) {
            throw new LecturaFallida("La IA ha rechazado leer el documento");
        }
        if (StopReason.MAX_TOKENS.equals(motivo)) {
            throw new LecturaFallida("La respuesta de la IA se ha cortado por longitud: el documento es "
                    + "demasiado grande para leerlo de una vez");
        }
        return parsear(textoDe(respuesta));
    }

    /** El documento como bloques: un PDF tal cual; un excel, volcado a texto. */
    static List<ContentBlockParam> documento(byte[] fichero, String nombreFichero) throws IOException {
        List<ContentBlockParam> bloques = new ArrayList<>();
        String nombre = nombreFichero == null ? "" : nombreFichero;
        if (esPdf(fichero)) {
            bloques.add(texto("PDF del pedido de SMS" + (nombre.isBlank() ? "" : " \"" + nombre + "\"") + ":"));
            bloques.add(ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder().data(Base64.getEncoder().encodeToString(fichero)).build())
                    .build()));
        } else {
            String encabezado = "Excel del pedido de SMS" + (nombre.isBlank() ? "" : " \"" + nombre + "\"") + ":";
            bloques.add(texto(VolcadoExcel.texto(fichero, encabezado, MAXIMO_FILAS_POR_HOJA, MAXIMO_COLUMNAS)));
        }
        return bloques;
    }

    static boolean esPdf(byte[] fichero) {
        return fichero != null && fichero.length > 4 && fichero[0] == '%' && fichero[1] == 'P'
                && fichero[2] == 'D' && fichero[3] == 'F';
    }

    /** La petición entera. Package-private para los tests. */
    static MessageCreateParams peticionPara(List<ContentBlockParam> documento, String claveCliente,
                                            String nombreCliente) {
        List<ContentBlockParam> bloques = new ArrayList<>();
        bloques.add(texto("Cliente elegido en la aplicación: " + nombreCliente + " (clave " + claveCliente + ")"));
        bloques.addAll(documento);
        bloques.add(texto("Saca las líneas de la hoja de corte de este pedido de SMS."));
        return MessageCreateParams.builder()
                .model(Model.of(MODELO))
                .maxTokens(TOKENS_MAXIMOS)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.HIGH)
                        .format(JsonOutputFormat.builder().schema(esquema()).build())
                        .build())
                .system(PROMPT)
                .addUserMessageOfBlockParams(bloques)
                .build();
    }

    static JsonOutputFormat.Schema esquema() {
        try {
            Map<String, Object> campos = new ObjectMapper().readValue(ESQUEMA,
                    new TypeReference<LinkedHashMap<String, Object>>() { });
            JsonOutputFormat.Schema.Builder constructor = JsonOutputFormat.Schema.builder();
            campos.forEach((clave, valor) -> constructor.putAdditionalProperty(clave, JsonValue.from(valor)));
            return constructor.build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("El esquema de la lectura de SMS no es JSON válido", e);
        }
    }

    /** La respuesta, aislando el objeto por si viniera texto alrededor. Package-private para los tests. */
    LecturaSmsIa parsear(String texto) {
        int inicio = texto.indexOf('{');
        int fin = texto.lastIndexOf('}');
        if (inicio < 0 || fin <= inicio) {
            throw new LecturaFallida("La respuesta de la IA no contiene un JSON");
        }
        try {
            return mapper.readValue(texto.substring(inicio, fin + 1), LecturaSmsIa.class);
        } catch (JsonProcessingException e) {
            throw new LecturaFallida("La respuesta de la IA no se entiende: " + e.getOriginalMessage(), e);
        }
    }

    private static ContentBlockParam texto(String contenido) {
        return ContentBlockParam.ofText(TextBlockParam.builder().text(contenido).build());
    }

    private Message acumular(MessageCreateParams peticion) {
        MessageAccumulator acumulador = MessageAccumulator.create();
        try (StreamResponse<RawMessageStreamEvent> flujo = clienteApi().messages().createStreaming(peticion)) {
            flujo.stream().forEach(acumulador::accumulate);
        }
        return acumulador.message();
    }

    private AnthropicClient clienteApi() {
        AnthropicClient actual = cliente;
        if (actual != null) {
            return actual;
        }
        synchronized (this) {
            if (cliente == null) {
                try {
                    cliente = AnthropicOkHttpClient.builder().fromEnv().timeout(TIEMPO_MAXIMO).build();
                } catch (RuntimeException e) {
                    throw new LecturaFallida("Falta configurar la clave del servicio de IA "
                            + "(variable de entorno ANTHROPIC_API_KEY)", e);
                }
            }
            return cliente;
        }
    }

    private static String textoDe(Message respuesta) {
        StringBuilder texto = new StringBuilder();
        for (ContentBlock bloque : respuesta.content()) {
            bloque.text().ifPresent(t -> texto.append(t.text()));
        }
        return texto.toString();
    }
}

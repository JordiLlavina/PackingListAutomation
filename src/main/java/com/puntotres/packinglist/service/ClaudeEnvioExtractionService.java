package com.puntotres.packinglist.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigEnabled;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.EnvioInput;

/**
 * Modo CLAUDE de la pantalla de entrada: manda los escaneos del packing list
 * (fotos o PDFs) a la API de Anthropic y convierte la respuesta en el mismo
 * {@link EnvioInput} que produce el JSON pegado a mano, de forma que el
 * resto del pipeline (importar → asignar → inferir) no distingue el origen.
 *
 * Las hojas reales son NOTAS MANUSCRITAS del operario de almacén con una
 * taquigrafía propia, no tablas: el prompt enseña esa taquigrafía (rangos
 * "Nº 15 - 83 x 5", cajas mixtas, apóstrofo decimal, tachados...) y se monta
 * por cliente — un núcleo común más un bloque por {@link TipoPlantilla},
 * porque lo que hay que leer en una hoja de AMI (referencia con puntos,
 * numeración continua) no tiene nada que ver con una de APC (referencia
 * parcial, pedido de tres dígitos, hoja por destinación). Las hojas de
 * ejemplo reales están en docs/Packing Lists/*IMAGENES.pdf y el análisis en
 * docs/Packing Lists/prompt-extraccion-claude.md.
 *
 * El cliente HTTP se crea perezosamente en el primer uso: así la aplicación
 * arranca (y los otros modos funcionan) aunque no esté configurada la
 * variable de entorno ANTHROPIC_API_KEY.
 */
@Service
public class ClaudeEnvioExtractionService {

    /** Un archivo subido: su media type (image/jpeg, application/pdf...) y sus bytes. */
    public record Adjunto(String mediaType, byte[] datos) {
    }

    /** Fallo de extracción con mensaje pensado para mostrarse al usuario. */
    public static class ExtraccionException extends RuntimeException {
        public ExtraccionException(String mensaje) { super(mensaje); }
        public ExtraccionException(String mensaje, Throwable causa) { super(mensaje, causa); }
    }

    private static final String MEDIA_TYPE_PDF = "application/pdf";

    /**
     * El razonamiento y el JSON de salida comparten el techo de {@code
     * max_tokens}: descifrar siete páginas de caligrafía consume MUCHO
     * razonamiento, y con presupuesto adaptativo se comía el techo entero y
     * la respuesta llegaba cortada ("se ha cortado por longitud"), sin JSON.
     * Por eso el presupuesto de razonamiento es explícito y el techo es la
     * suma: así la salida tiene sitio garantizado pase lo que pase.
     */
    private static final long TOKENS_RAZONAMIENTO = 16_000L;
    private static final long TOKENS_SALIDA = 16_000L;

    /**
     * Una extracción larga puede tardar varios minutos. Se pide en streaming
     * (y se acumula) porque una petición normal tan larga acaba en timeout de
     * lectura, y el timeout del cliente se sube en consecuencia.
     */
    private static final Duration TIEMPO_MAXIMO = Duration.ofMinutes(15);

    private static final String PROMPT_NUCLEO = """
            Transcribes packing lists escritos A MANO por el operario de almacén de un \
            fabricante de marroquinería (Punto Tres). La entrada son escaneos de hojas \
            manuscritas: son notas en taquigrafía, NO tablas. Devuelves EXCLUSIVAMENTE un \
            JSON válido, sin markdown, sin comentarios y sin texto antes o después.

            ## Cómo se leen estas hojas

            Cada hoja lista, para una destinación, qué referencias se han empaquetado y
            en qué caja física ha ido cada una. Anatomía típica de un bloque:

              MOD <referencia> <color>             cabecera de artículo
              <destinación> (<pedido>): <total>    destinación, nº de pedido y total pedido
              <n>/<talla>, <n>/<talla>, ...        unidades pedidas por talla (SUMA DE CONTROL)
              Nº 1, 2 x 61: 122/75    13'52kg      cajas 1 y 2, 61 uds cada una, peso por caja
              Nº 15 - 83 x 5 sacs  345 (69 CAJAS)  RANGO de cajas 15..83, 5 uds cada una
              60x40x40                             medida de caja del grupo de arriba

            ## Reglas de lectura

            1. "Nº" introduce SIEMPRE números de caja, nunca cantidades.
               - "Nº 1, 2 x 61" = cajas 1 y 2, 61 unidades CADA UNA (no 61 repartidas).
               - "Nº 15 - 83 x 5" = rango de las cajas 15 a 83, 5 unidades cada una.
               - "x" significa "cada una contiene". "sacs" = unidades. "cajas" = cajas.
               - "(69 CAJAS)" es un recuento de control: 83-15+1 = 69.

            2. La línea "<n>/<talla>, <n>/<talla>, ..." que va justo debajo de la
               cabecera del artículo son las UNIDADES PEDIDAS POR TALLA. NO es una caja
               y no genera nunca entradas de "cajas": va a "cantidadTotal", una entrada
               de "referencias" por talla. Una "t" o un "+" delante de la talla son la
               T de talla: "45/t75" son 45 unidades de la talla 75, la talla es "75",
               nunca "t75".

            3. Copia "cantidadTotal" de esa línea tal como está escrita. NO la
               recalcules sumando las cajas y NO cuadres las dos si difieren: un
               descuadre es una discrepancia real que el operario tiene que ver.

            4. Lo TACHADO no existe: si un bloque o una línea está cruzado por una
               raya, omítelo entero — no debe generar ninguna referencia ni ninguna
               caja. Si una cifra está escrita encima de otra, o rodeada con un
               círculo, vale la de encima / la rodeada: son la corrección final del
               operario.

            5. Los decimales se escriben con apóstrofo o coma: "13'52kg" son 13,52 y
               "12'820kg" son 12,820. En el JSON emite SIEMPRE un número JSON con
               punto decimal: 13.52. Nunca 13'52, nunca una cadena, nunca la unidad.

            6. "TODO Nº <n>" (o "todo caja <n>") significa que TODAS las referencias
               listadas encima van en esa única caja: repite ese número de caja en
               todas ellas. Es una caja mixta y es correcto.

            7. Las medidas de caja ("60x40x40") se escriben una vez para un grupo de
               cajas, a veces debajo y a veces de lado en el margen. Aplícalas a las
               cajas de su grupo, en "medidaCaja", como LxWxH en cm.

            8. Los números de caja son los del operario y van escritos físicamente en
               el bulto. Transcríbelos EXACTAMENTE: no renumeres, no cierres huecos y
               no fusiones repetidos. Un mismo número de caja bajo varias referencias
               es una caja mixta y es correcto.

            9. "pesoBruto" (kg) es el peso de la CAJA FÍSICA ENTERA y es OPCIONAL.
               - En un rango es el peso de CADA UNA de sus cajas.
               - En una caja mixta (mismo nº de caja en varias entradas) va UNA SOLA
                 VEZ, en la primera entrada. Nunca lo repartas ni lo repitas.
               - Si en una línea hay DOS números de peso, el bruto es el que lleva
                 "kg"; si los dos o ninguno lo llevan, el mayor, y apúntalo en
                 "avisos".
               - Si la hoja no da peso, omite el campo. No lo estimes nunca.

            10. Lo normal es que las hojas digan QUÉ CAJAS van en cada palet con una
                tabla tipo "1  1-12 / 2  13-24": eso es lo que va a "palets". La tabla
                puede venir en una hoja aparte (incluso la primera del documento) o al
                final de la última hoja de la destinación, y vale para la DESTINACIÓN
                ENTERA, todas sus referencias, no solo para la hoja donde está
                escrita. Si NO hay reparto escrito, deja "palets" vacío: se completa
                después a mano. Un simple RECUENTO ("5 palets") NUNCA basta para
                inventar rangos — un palet inventado acaba impreso en una etiqueta
                pegada a un bulto real. Los recuentos van a "resumenPalets".

            11. Las hojas van numeradas (a menudo con el número rodeado arriba a la
                derecha). Léelas en orden: un bloque puede continuar en la hoja
                siguiente, y entonces es UN solo bloque, no dos. Si un bloque quedó
                CORTADO al final de una hoja y la siguiente lo repite entero, cuenta
                solo la versión completa: no dupliques la referencia.

            12. Un número rodeado en el MARGEN IZQUIERDO de uno o varios bloques es el
                número de caja de todos ellos (equivale a "TODO Nº <n>"). Las
                anotaciones en otro bolígrafo o rotulador también son datos: suelen
                ser correcciones o aclaraciones posteriores.

            13. Todo lo que no puedas leer con seguridad, y todo lo que el operario
                haya marcado con "?" o "!?", va igualmente en el campo con tu mejor
                lectura Y además como una línea de "avisos" diciendo qué es dudoso y
                dónde. Nunca dejes un campo mal en silencio.

            ## Estructura exacta del JSON

            {
              "cliente": "<clave del cliente si aparece; si no, omítelo>",
              "avisos": ["<dudas y descuadres, en español>"],
              "resumenPalets": [
                { "destino": "<destinación>", "palets": 5 }
              ],
              "destinos": [
                {
                  "destino": "<nombre de la destinación>",
                  "palets": [ { "palet": 1, "cajaInicio": 1, "cajaFin": 12 } ],
                  "referencias": [
                    {
                      "referencia": "...",
                      "color": "...",
                      "medidaCaja": "60x40x40",
                      "pedido": "...",
                      "talla": "<solo si el artículo tiene talla>",
                      "modelo": "<solo si el cliente lo usa>",
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

            Cada entrada de "cajas" tiene UNA de las dos formas: caja suelta
            {"caja": N, "unidades": U} o rango {"cajaInicio": A, "cajaFin": B,
            "unidadesPorCaja": U}. Un rango implica que TODAS esas cajas llevan las
            mismas unidades; si no, usa cajas sueltas.

            Una referencia con varias tallas o colores son varias entradas de
            "referencias", una por combinación, cada una con su "cantidadTotal".

            Copia referencias, colores y pedidos EXACTAMENTE como aparecen,
            respetando puntos, ceros a la izquierda y mayúsculas.

            "resumenPalets" recoge los RECUENTOS de palets por destinación cuando
            alguna hoja los declara ("Japan -> 1 palet", "wh. 5 palet"): una entrada
            por destinación mencionada, aunque no tengas su reparto de cajas. Si
            ninguna hoja da recuentos, omite el campo. La aplicación lo usa para
            comprobar que no falta ninguna hoja.
            """;

    private static final String BLOQUE_AMI = """

            ## Este envío es de AMI

            - Cabecera de artículo: "MOD UBL029.AL0216.2221 chocolat". Sepárala así:
                "referencia" = los DOS primeros grupos de puntos -> "UBL029.AL0216"
                "color"      = el TERCER grupo, solo el código -> "2221"
              Las palabras finales ("chocolat", "DARK COFFEE") son el nombre del
              color: NO van a ningún campo. La referencia sigue siempre el patrón
              3 letras + 3 dígitos, punto, 2 letras + 4 dígitos: úsalo para
              autocorregir confusiones de caligrafía (O/0, L/C, I/1). Prefijos:
              ULL = bolso, USL = cartera, UBL = cinturón.

            - Los cinturones (UBL) llevan "talla" (75, 85, 95, 105): una entrada de
              "referencias" por talla, cada una con su "cantidadTotal". Los bolsos y
              carteras no llevan talla: omite el campo.

            - La destinación y el pedido van juntos: "PARIS 07672:" es destino PARIS
              y pedido "07672". Respeta los ceros a la izquierda.

            - Los números de caja son CONTINUOS entre las hojas y las referencias de
              una misma destinación (si la hoja 1 acaba en la caja 11, la hoja 2
              empieza en la 12 aunque cambie de artículo). No reinicies en 1 por hoja
              ni por referencia.

            - La tabla caja→palet ("1  1-12 / 2  13-24 ...") suele venir al final de
              la última hoja de la destinación, y cubre TODAS sus cajas, de todas las
              referencias. Transcríbela entera en "palets". Puede llevar anotaciones
              encima (tachones, letras en rotulador): apúntalas en "avisos".

            - NO rellenes "modelo", "canal" ni "livraisonCode": AMI no los usa.
            """;

    private static final String BLOQUE_APC = """

            ## Este envío es de APC

            - Cabecera de artículo: "MOD 67043 SJ sac Le Neige CLOU CAMEL". Sepárala:
                "referencia" = el código tal cual está escrito -> "67043", "F63023"
                               Está INCOMPLETO a propósito: NO lo amplíes ni le
                               añadas prefijos, lo completa la aplicación con el
                               excel de pedido del cliente. Las siglas sueltas
                               detrás del código ("SJ", "SS") NO son parte de la
                               referencia: déjalas fuera y, si dudas de qué son,
                               una línea en "avisos".
                "modelo"     = el nombre descriptivo -> "sac Le Neige",
                               "Pochette Neige", "Ceinture Rosette Antik"
                "color"      = el color como está, con su código si lo lleva ->
                               "CLOU CAMEL", "LZZ Negro", "KBE Olive"

            - Debajo va "<destinación> (<pedido>): <total> <color>", p. ej.
              "Retail (705): 2 camel". El número entre paréntesis es el NÚMERO DE
              PEDIDO y normalmente son solo sus TRES ÚLTIMOS DÍGITOS (705, 682, 860).
              Transcribe esos dígitos EXACTAMENTE: no los rellenes, no inventes el
              resto; la aplicación completa el número desde el excel de pedido. Si el
              operario lo escribe entero (4100128715), cópialo entero.

            - El número de pedido puede repetirse entre paréntesis en las líneas de
              caja ("Nº 4 x 25 Liquen (682) Japan"): ahí es el MISMO pedido, no una
              cantidad.

            - Destinación: hay UNA HOJA POR DESTINACIÓN, titulada arriba ("APC Japan",
              "APC Retail", "APC Douanes USA"). Usa el nombre del catálogo:
                Japan -> JAPAN
                Korea -> KOREA
                Douanes USA / D. USA -> D. USA
                Ivry -> IVRY
                Retail -> RETAIL
                Wholesale concess -> WHOLESALE CONCESS
                Wh. / Wholesale -> WHOLESALE
                Australia -> AUSTRALIA
                Chine franch -> CHINE FRANCH
              Desarrolla las abreviaturas ("wh." es WHOLESALE). Si la hoja dice una
              destinación que no está en esta lista, cópiala tal cual Y añade una
              línea a "avisos".

            - "canal": solo si una línea concreta marca un canal distinto del de la
              hoja. Si no, omítelo: lo rellena la aplicación.

            - Los cinturones llevan "talla" de 5 en 5 (75, 80, 85, 90, 95, 100): una
              entrada de "referencias" por talla.

            - Los números de caja REINICIAN EN 1 en cada destinación.

            - El reparto de palets puede venir en una hoja resumen APARTE, incluso la
              primera del documento, con una línea por destinación ("Japan -> 1 palet
              1-6", "wh. 5 palet"). Rango de cajas escrito -> a "palets" de esa
              destinación; solo un recuento -> "palets" vacío y el recuento a
              "resumenPalets".

            - NO rellenes nunca "livraisonCode": la aplicación lo genera y se ignora
              el que venga en la entrada.
            """;

    private static final String BLOQUE_GENERICO = """

            ## Este envío es de un cliente de plantilla genérica

            - Lo que importa es "referencia", "color" y "medidaCaja"; añade "modelo"
              si en la hoja aparece un nombre descriptivo del artículo.
            - Añade "pedido" si aparece en la hoja.
            - NO rellenes "talla", "canal" ni "livraisonCode": estas plantillas no
              los usan.
            """;

    /** Package-private: lo copia también la guía de uso manual en claude.ai. */
    static final String MENSAJE_USUARIO = """
            Transcribe al JSON descrito el packing list de estos documentos. Todas \
            las hojas son del MISMO envío y van en orden: un bloque puede continuar \
            en la hoja siguiente. Antes de responder, comprueba caja por caja que no \
            has inventado ninguna, que no has transcrito nada tachado y que todos \
            los pesos son números con punto decimal.""";

    private final ObjectMapper mapper;
    private volatile AnthropicClient cliente;

    public ClaudeEnvioExtractionService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Extrae el envío de los adjuntos (imágenes o PDFs) con el prompt del
     * tipo de plantilla del cliente. Lanza {@link ExtraccionException} con
     * un mensaje legible si falta la clave de API, la llamada falla o la
     * respuesta no es un JSON interpretable.
     */
    public EnvioInput extraer(List<Adjunto> adjuntos, TipoPlantilla plantilla) {
        Message respuesta;
        try {
            respuesta = acumular(peticionPara(adjuntos, plantilla));
        } catch (AnthropicServiceException e) {
            throw new ExtraccionException("La API de Claude ha devuelto un error: "
                    + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new ExtraccionException("No se pudo conectar con la API de Claude: "
                    + e.getMessage(), e);
        }

        StopReason motivoParada = respuesta.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(motivoParada)) {
            throw new ExtraccionException("Claude ha rechazado procesar los documentos");
        }
        if (StopReason.MAX_TOKENS.equals(motivoParada)) {
            throw new ExtraccionException("La respuesta de Claude se ha cortado por longitud "
                    + "aun con el presupuesto ampliado: parte el envío en menos hojas "
                    + "(por ejemplo, una destinación cada vez) y pega los JSON a mano");
        }
        return parsear(textoDe(respuesta));
    }

    /**
     * La petición completa: documentos, mensaje de usuario, prompt del
     * cliente y presupuestos de tokens. Package-private para los tests.
     */
    static MessageCreateParams peticionPara(List<Adjunto> adjuntos, TipoPlantilla plantilla) {
        List<ContentBlockParam> bloques = bloquesDe(adjuntos);
        bloques.add(ContentBlockParam.ofText(TextBlockParam.builder()
                .text(MENSAJE_USUARIO).build()));
        return MessageCreateParams.builder()
                .model(Model.CLAUDE_OPUS_4_8)
                .maxTokens(TOKENS_RAZONAMIENTO + TOKENS_SALIDA)
                .thinking(ThinkingConfigEnabled.builder()
                        .budgetTokens(TOKENS_RAZONAMIENTO).build())
                .system(promptPara(plantilla))
                .addUserMessageOfBlockParams(bloques)
                .build();
    }

    /** Núcleo común + bloque del cliente. Package-private para los tests. */
    static String promptPara(TipoPlantilla plantilla) {
        return PROMPT_NUCLEO + switch (plantilla) {
            case AMI -> BLOQUE_AMI;
            case APC -> BLOQUE_APC;
            case GENERIC -> BLOQUE_GENERICO;
        };
    }

    /**
     * Un bloque por adjunto, precedido de su ordinal: los PDFs van como
     * documento (la API renderiza cada página) y las imágenes como imagen.
     * Package-private para los tests.
     */
    static List<ContentBlockParam> bloquesDe(List<Adjunto> adjuntos) {
        List<ContentBlockParam> bloques = new ArrayList<>();
        for (int i = 0; i < adjuntos.size(); i++) {
            Adjunto adjunto = adjuntos.get(i);
            bloques.add(ContentBlockParam.ofText(TextBlockParam.builder()
                    .text("Documento " + (i + 1) + ":").build()));
            String base64 = Base64.getEncoder().encodeToString(adjunto.datos());
            if (esPdf(adjunto.mediaType())) {
                bloques.add(ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                        .source(Base64PdfSource.builder().data(base64).build())
                        .build()));
            } else {
                bloques.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                        .source(Base64ImageSource.builder()
                                .mediaType(mediaTypeDe(adjunto.mediaType()))
                                .data(base64)
                                .build())
                        .build()));
            }
        }
        return bloques;
    }

    private static boolean esPdf(String mediaType) {
        return mediaType != null && MEDIA_TYPE_PDF.equals(mediaType.toLowerCase(Locale.ROOT));
    }

    /**
     * Consume el streaming hasta el final y devuelve el mensaje completo,
     * igual que si se hubiera pedido de una pieza. El streaming no es por
     * enseñar nada al usuario: es lo que permite pedir un presupuesto de
     * tokens grande sin que la petición muera por timeout de lectura.
     */
    private Message acumular(MessageCreateParams peticion) {
        MessageAccumulator acumulador = MessageAccumulator.create();
        try (StreamResponse<RawMessageStreamEvent> flujo =
                     clienteApi().messages().createStreaming(peticion)) {
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
                    cliente = AnthropicOkHttpClient.builder()
                            .fromEnv()
                            .timeout(TIEMPO_MAXIMO)
                            .build();
                } catch (RuntimeException e) {
                    throw new ExtraccionException("Falta configurar la clave de la API de Claude "
                            + "(variable de entorno ANTHROPIC_API_KEY)", e);
                }
            }
            return cliente;
        }
    }

    private static Base64ImageSource.MediaType mediaTypeDe(String mediaType) {
        String tipo = mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT);
        return switch (tipo) {
            case "image/jpeg", "image/jpg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
            case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
            case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
            case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
            default -> throw new ExtraccionException(
                    "Formato de archivo no soportado: " + mediaType
                            + " (usa PDF, JPEG, PNG, GIF o WebP)");
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
     * Package-private para los tests.
     */
    EnvioInput parsear(String texto) {
        int inicio = texto.indexOf('{');
        int fin = texto.lastIndexOf('}');
        if (inicio < 0 || fin <= inicio) {
            throw new ExtraccionException("La respuesta de Claude no contiene un JSON");
        }
        String json = normalizarApostrofoDecimal(texto.substring(inicio, fin + 1));
        try {
            return mapper.readValue(json, EnvioInput.class);
        } catch (JsonProcessingException e) {
            throw new ExtraccionException("El JSON devuelto por Claude no es válido: "
                    + e.getOriginalMessage(), e);
        }
    }

    /**
     * Red de seguridad de la regla 5 del prompt: el operario escribe los
     * decimales con apóstrofo ("13'52kg") y, si el modelo lo copiara tal
     * cual, el JSON entero sería ilegible y la importación moriría sin
     * diagnóstico. Dígito-apóstrofo-dígito no aparece en ningún dato legítimo
     * (las palabras francesas con apóstrofo lo llevan entre letras).
     */
    private static String normalizarApostrofoDecimal(String json) {
        return json.replaceAll("(\\d)'(\\d)", "$1.$2");
    }
}

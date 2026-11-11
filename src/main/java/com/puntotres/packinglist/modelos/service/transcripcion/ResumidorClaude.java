package com.puntotres.packinglist.modelos.service.transcripcion;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.modelos.ModelosProperties;

/**
 * Resume con Claude la transcripción de una nota de voz para el despacho:
 * título de una línea, resumen breve, tipo de cambio sugerido (una clave de
 * {@code modelos.tipos-cambio}), materiales y dudas de lectura. Es un
 * accesorio: si falla, la transcripción sigue valiendo.
 *
 * Cómo se llama, por lo aprendido en {@code ConversionFormatoTallerService}:
 * razonamiento ADAPTATIVO con el esfuerzo en {@code outputConfig} (Opus 5.5
 * rechaza {@code thinking.enabled} con un 400 en tiempo de ejecución), y
 * salida estructurada con el esquema {@link #ESQUEMA}, que es el de
 * {@link ResumenIa}. Aquí el esfuerzo es BAJO y la petición va sin streaming:
 * la respuesta son cinco campos cortos y el texto de entrada, como mucho tres
 * minutos de voz, así que cabe de sobra en una petición normal con un tiempo
 * de espera de dos minutos.
 *
 * El cliente de la API se crea la primera vez que hace falta: sin la clave la
 * aplicación arranca igual (y {@link TranscripcionConfig} ni siquiera elige
 * esta clase).
 */
public class ResumidorClaude implements ResumidorModificacion {

    static final String MODELO = "claude-opus-5-5";

    static final String VARIABLE_CLAVE = "ANTHROPIC_API_KEY";

    /** Razonamiento y JSON comparten el techo; la salida son unas líneas. */
    private static final long TOKENS_MAXIMOS = 8_000L;

    private static final Duration TIEMPO_MAXIMO = Duration.ofMinutes(2);

    /**
     * El esquema del JSON de respuesta: sigue a {@link ResumenIa}, todos los
     * campos obligatorios y sin uniones con null (la API limita a 16 los
     * campos con unión de tipos): un texto que no hay es la cadena vacía.
     */
    static final String ESQUEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "titulo": {"type": "string", "description": "Título de una línea, de menos de 80 caracteres"},
                "resumen": {"type": "string", "description": "Resumen breve, de una a tres frases"},
                "tipoCambioSugerido": {"type": "string",
                  "description": "Una de las claves de tipo de cambio, o vacío si no está claro"},
                "materiales": {"type": "array", "items": {"type": "string"}},
                "dudas": {"type": "array", "items": {"type": "string"}}
              },
              "required": ["titulo", "resumen", "tipoCambioSugerido", "materiales", "dudas"]
            }""";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ModelosProperties props;
    private final UnaryOperator<String> entorno;
    private volatile AnthropicClient cliente;

    /**
     * @param entorno de dónde se leen las variables de entorno ({@code System::getenv}
     *                en la aplicación; un mapa en los tests)
     */
    public ResumidorClaude(ModelosProperties props, UnaryOperator<String> entorno) {
        this.props = props;
        this.entorno = entorno;
    }

    @Override
    public boolean disponible() {
        String clave = entorno.apply(VARIABLE_CLAVE);
        return clave != null && !clave.isBlank();
    }

    @Override
    public ResumenIa resumir(String transcripcion, String tipoCambio, String modelo) throws IOException {
        Message respuesta;
        try {
            respuesta = clienteApi().messages().create(peticionPara(transcripcion, tipoCambio, modelo));
        } catch (AnthropicServiceException e) {
            throw new IOException("El servicio de IA ha devuelto un error: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new IOException("No se pudo conectar con el servicio de IA: " + e.getMessage(), e);
        }
        StopReason motivoParada = respuesta.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(motivoParada)) {
            throw new IOException("La IA no ha querido resumir la nota de voz");
        }
        if (StopReason.MAX_TOKENS.equals(motivoParada)) {
            throw new IOException("La respuesta de la IA se ha cortado por longitud");
        }
        return parsear(textoDe(respuesta));
    }

    /** La petición: prompt de sistema con los tipos del yml, la transcripción y su contexto. */
    MessageCreateParams peticionPara(String transcripcion, String tipoCambio, String modelo) {
        return MessageCreateParams.builder()
                .model(Model.of(MODELO))
                .maxTokens(TOKENS_MAXIMOS)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(JsonOutputFormat.builder().schema(esquema()).build())
                        .build())
                .system(prompt())
                .addUserMessage("Modelo: " + texto(modelo, "(sin nombre)") + "\n"
                        + "Tipo de cambio elegido por el taller: " + texto(tipoCambio, "(no lo eligió)") + "\n\n"
                        + "Transcripción de la nota de voz:\n\"\"\"\n" + texto(transcripcion, "") + "\n\"\"\"")
                .build();
    }

    /** El prompt de sistema, con las claves de {@code modelos.tipos-cambio}. */
    String prompt() {
        String claves = props.tiposCambio() == null ? ""
                : props.tiposCambio().stream().map(ModelosProperties.TipoCambio::clave)
                        .collect(Collectors.joining(", "));
        return "Eres el asistente de despacho de un taller de marroquinería (Puntotres). El técnico del taller ha "
                + "dictado una nota de voz sobre un cambio hecho en un modelo. Resume para despacho, sin inventar: "
                + "título de una línea, resumen breve, tipo de cambio sugerido entre " + claves + ", materiales "
                + "mencionados, dudas de lectura si algo no se entiende.\n\n"
                + "Escribe en español. El tipo de cambio sugerido es una de esas claves tal cual, o vacío si la "
                + "nota no deja claro cuál es. Los materiales son solo los que se nombran en la nota (pieles, "
                + "forros, hilos, herrajes), con lo que se diga de ellos. Si una palabra no se entiende o la "
                + "transcripción parece haberla oído mal, dilo en dudas en vez de corregirla a tu manera. Lo que "
                + "no se dice no se rellena: una lista vacía o un texto vacío es una respuesta correcta.";
    }

    /** {@link #ESQUEMA} como lo pide el SDK. */
    static JsonOutputFormat.Schema esquema() {
        try {
            Map<String, Object> campos = JSON.readValue(ESQUEMA, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            JsonOutputFormat.Schema.Builder constructor = JsonOutputFormat.Schema.builder();
            campos.forEach((clave, valor) -> constructor.putAdditionalProperty(clave, JsonValue.from(valor)));
            return constructor.build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("El esquema del resumen no es JSON válido", e);
        }
    }

    /**
     * La respuesta como {@link ResumenIa}, sin nulos: un texto que falta es
     * vacío y una lista que falta, vacía. Con la salida estructurada viene ya
     * como JSON limpio; se aísla el objeto por si algún día se quitara el
     * esquema. Package-private para los tests.
     */
    static ResumenIa parsear(String json) throws IOException {
        String limpio = json == null ? "" : json;
        int inicio = limpio.indexOf('{');
        int fin = limpio.lastIndexOf('}');
        JsonNode raiz = null;
        if (inicio >= 0 && fin > inicio) {
            try {
                raiz = JSON.readTree(limpio.substring(inicio, fin + 1));
            } catch (JsonProcessingException noSeEntiende) {
                raiz = null;
            }
        }
        if (raiz == null || !raiz.isObject()) {
            String muestra = limpio.length() <= 200 ? limpio : limpio.substring(0, 200) + "…";
            throw new IOException("La respuesta de la IA no se entiende: " + muestra);
        }
        return new ResumenIa(textoDe(raiz, "titulo"), textoDe(raiz, "resumen"), textoDe(raiz, "tipoCambioSugerido"),
                listaDe(raiz, "materiales"), listaDe(raiz, "dudas"));
    }

    private static String textoDe(JsonNode raiz, String campo) {
        JsonNode valor = raiz.get(campo);
        return valor == null || valor.isNull() ? "" : valor.asText().strip();
    }

    private static List<String> listaDe(JsonNode raiz, String campo) {
        JsonNode valor = raiz.get(campo);
        List<String> lista = new ArrayList<>();
        if (valor != null && valor.isArray()) {
            for (JsonNode elemento : valor) {
                String texto = elemento.isNull() ? "" : elemento.asText().strip();
                if (!texto.isEmpty()) {
                    lista.add(texto);
                }
            }
        }
        return List.copyOf(lista);
    }

    private static String texto(String valor, String siVacio) {
        return valor == null || valor.isBlank() ? siVacio : valor.strip();
    }

    private static String textoDe(Message respuesta) {
        StringBuilder texto = new StringBuilder();
        for (ContentBlock bloque : respuesta.content()) {
            bloque.text().ifPresent(t -> texto.append(t.text()));
        }
        return texto.toString();
    }

    private AnthropicClient clienteApi() throws IOException {
        AnthropicClient actual = cliente;
        if (actual != null) {
            return actual;
        }
        synchronized (this) {
            if (cliente == null) {
                try {
                    cliente = AnthropicOkHttpClient.builder()
                            .fromEnv()
                            .apiKey(texto(entorno.apply(VARIABLE_CLAVE), ""))
                            .timeout(TIEMPO_MAXIMO)
                            .build();
                } catch (RuntimeException e) {
                    throw new IOException("Falta configurar la clave del servicio de IA (variable de entorno "
                            + VARIABLE_CLAVE + ")", e);
                }
            }
            return cliente;
        }
    }
}

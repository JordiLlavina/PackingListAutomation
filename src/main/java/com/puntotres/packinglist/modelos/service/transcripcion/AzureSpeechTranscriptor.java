package com.puntotres.packinglist.modelos.service.transcripcion;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.modelos.ModelosProperties;

/**
 * Transcribe con la «transcripción rápida» de Azure Speech
 * ({@code speechtotext/transcriptions:transcribe}, versión 2024-11-15): una
 * sola petición síncrona con el audio, sin subirlo antes a ningún sitio y sin
 * sondear un trabajo por lotes. Acepta WebM/Opus (Chrome) y MP4 (Safari) tal
 * cual, que es lo que graba la PWA.
 *
 * <pre>
 * POST https://{region}.api.cognitive.microsoft.com/speechtotext/transcriptions:transcribe?api-version=2024-11-15
 * Ocp-Apim-Subscription-Key: {clave}
 * multipart/form-data: audio = los bytes, definition = {"locales":["es-ES"]}
 * → {"combinedPhrases": [{"text": "..."}], "phrases": [...]}
 * </pre>
 *
 * El {@link RestClient.Builder} llega ya con los tiempos de espera puestos
 * ({@link TranscripcionConfig}): aquí no se toca su fábrica de peticiones, para
 * que el test pueda colgarle un {@code MockRestServiceServer}.
 */
public class AzureSpeechTranscriptor implements Transcriptor {

    static final String VERSION_API = "2024-11-15";

    /** Español de España: es lo que habla el taller. */
    static final String DEFINICION = "{\"locales\":[\"es-ES\"]}";

    /** Lo que se copia del cuerpo de una respuesta de error al mensaje. */
    private static final int LARGO_CUERPO_ERROR = 300;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient cliente;
    private final String clave;
    private final String region;

    public AzureSpeechTranscriptor(RestClient.Builder builder, ModelosProperties props) {
        this.cliente = builder.build();
        ModelosProperties.AzureSpeech azure = props.azureSpeech();
        this.clave = azure == null || azure.clave() == null ? "" : azure.clave().trim();
        this.region = azure == null || azure.region() == null ? "" : azure.region().trim();
    }

    @Override
    public boolean disponible() {
        return !clave.isEmpty() && !region.isEmpty();
    }

    @Override
    public String transcribir(byte[] audio, String mime) throws IOException {
        // Sin MultipartBodyBuilder: tira de las clases reactivas, que esta aplicación no lleva.
        String nombre = nombreFichero(mime);
        HttpHeaders cabecerasAudio = new HttpHeaders();
        cabecerasAudio.setContentType(tipo(mime));
        HttpHeaders cabecerasDefinicion = new HttpHeaders();
        cabecerasDefinicion.setContentType(MediaType.APPLICATION_JSON);
        MultiValueMap<String, HttpEntity<?>> partes = new LinkedMultiValueMap<>();
        partes.add("audio", new HttpEntity<>(new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return nombre;
            }
        }, cabecerasAudio));
        partes.add("definition", new HttpEntity<>(DEFINICION, cabecerasDefinicion));
        Respuesta respuesta;
        try {
            respuesta = cliente.post()
                    .uri(URI.create("https://" + region + ".api.cognitive.microsoft.com"
                            + "/speechtotext/transcriptions:transcribe?api-version=" + VERSION_API))
                    .header("Ocp-Apim-Subscription-Key", clave)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(partes)
                    .exchange((peticion, contestacion) -> new Respuesta(contestacion.getStatusCode().value(),
                            new String(contestacion.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException sinRespuesta) {
            throw new IOException("No se pudo conectar con Azure Speech: " + sinRespuesta.getMessage(), sinRespuesta);
        }
        if (respuesta == null) {
            throw new IOException("Azure Speech no ha contestado");
        }
        if (respuesta.estado() < 200 || respuesta.estado() >= 300) {
            String cuerpo = respuesta.cuerpo().strip();
            throw new IOException("Azure Speech ha contestado " + respuesta.estado()
                    + (cuerpo.isEmpty() ? "" : ": " + recortar(cuerpo, LARGO_CUERPO_ERROR)));
        }
        return textoCombinado(respuesta.cuerpo());
    }

    /** El texto de la primera frase combinada; sin ninguna (un silencio), vacío. */
    static String textoCombinado(String cuerpo) throws IOException {
        JsonNode raiz;
        try {
            raiz = JSON.readTree(cuerpo);
        } catch (IOException noEsJson) {
            throw new IOException("La respuesta de Azure Speech no se entiende: "
                    + recortar(cuerpo, LARGO_CUERPO_ERROR), noEsJson);
        }
        JsonNode frases = raiz == null ? null : raiz.get("combinedPhrases");
        if (frases == null || !frases.isArray() || frases.isEmpty()) {
            return "";
        }
        JsonNode texto = frases.get(0).get("text");
        return texto == null || texto.isNull() ? "" : texto.asText();
    }

    /**
     * Un nombre con la extensión de su tipo: Azure lo usa para reconocer el
     * contenedor. Sin tipo, {@code nota.webm}, que es lo que graba Chrome.
     */
    static String nombreFichero(String mime) {
        String tipo = sinParametros(mime);
        if (tipo.endsWith("/mp4") || tipo.endsWith("/m4a") || tipo.endsWith("/x-m4a") || tipo.endsWith("/aac")) {
            return "nota.m4a";
        }
        if (tipo.endsWith("/ogg")) {
            return "nota.ogg";
        }
        if (tipo.endsWith("/wav") || tipo.endsWith("/x-wav") || tipo.endsWith("/wave")) {
            return "nota.wav";
        }
        if (tipo.endsWith("/mpeg") || tipo.endsWith("/mp3")) {
            return "nota.mp3";
        }
        return "nota.webm";
    }

    private static MediaType tipo(String mime) {
        try {
            return mime == null || mime.isBlank() ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(mime);
        } catch (InvalidMediaTypeException noSeEntiende) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String sinParametros(String mime) {
        if (mime == null) {
            return "";
        }
        int puntoYComa = mime.indexOf(';');
        return (puntoYComa >= 0 ? mime.substring(0, puntoYComa) : mime).trim().toLowerCase(Locale.ROOT);
    }

    private static String recortar(String texto, int largo) {
        return texto.length() <= largo ? texto : texto.substring(0, largo) + "…";
    }

    private record Respuesta(int estado, String cuerpo) {
    }
}

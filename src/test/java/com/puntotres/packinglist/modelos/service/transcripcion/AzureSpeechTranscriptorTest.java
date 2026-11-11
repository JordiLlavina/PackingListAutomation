package com.puntotres.packinglist.modelos.service.transcripcion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.puntotres.packinglist.modelos.ModelosProperties;

/**
 * La llamada a Azure Speech (transcripción rápida) contra un servidor falso:
 * la URL de la región, la clave en su cabecera y el multipart con el audio y
 * la definición. No sale nada a la red.
 */
class AzureSpeechTranscriptorTest {

    private static final String URL = "https://westeurope.api.cognitive.microsoft.com"
            + "/speechtotext/transcriptions:transcribe?api-version=2024-11-15";

    private static final byte[] AUDIO = {26, 69, -33, -93, 1, 2, 3};

    @Test
    void mandaElAudioConLaClaveYLeeElTextoCombinado() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Ocp-Apim-Subscription-Key", "clave-secreta"))
                .andExpect(peticion -> {
                    MockClientHttpRequest mock = (MockClientHttpRequest) peticion;
                    String tipo = mock.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                    assertTrue(tipo != null && tipo.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE), tipo);
                    String cuerpo = mock.getBodyAsString(StandardCharsets.ISO_8859_1);
                    assertTrue(cuerpo.contains("name=\"audio\"; filename=\"nota.webm\""), cuerpo);
                    assertTrue(cuerpo.contains("name=\"definition\""), cuerpo);
                    assertTrue(cuerpo.contains("{\"locales\":[\"es-ES\"]}"), cuerpo);
                    assertTrue(cuerpo.contains(new String(AUDIO, StandardCharsets.ISO_8859_1)), "van los bytes");
                })
                .andRespond(withSuccess("{\"durationMilliseconds\":1200,\"combinedPhrases\":[{\"text\":\"hola\"}],"
                        + "\"phrases\":[]}", MediaType.APPLICATION_JSON));

        String texto = new AzureSpeechTranscriptor(builder, props("clave-secreta", "westeurope"))
                .transcribir(AUDIO, "audio/webm;codecs=opus");

        assertEquals("hola", texto);
        servidor.verify();
    }

    @Test
    void sinFrasesEsUnTextoVacio() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"combinedPhrases\":[]}", MediaType.APPLICATION_JSON));

        assertEquals("", new AzureSpeechTranscriptor(builder, props("clave", "westeurope"))
                .transcribir(AUDIO, "audio/mp4"));
    }

    @Test
    void unaRespuestaDeErrorEsUnaIOExceptionConSuEstado() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"401\",\"message\":\"Access denied due to invalid key\"}}"));

        IOException error = assertThrows(IOException.class,
                () -> new AzureSpeechTranscriptor(builder, props("mala", "westeurope")).transcribir(AUDIO,
                        "audio/webm"));
        assertTrue(error.getMessage().contains("401"), error.getMessage());
        assertTrue(error.getMessage().contains("invalid key"), error.getMessage());
    }

    @Test
    void elNombreDelFicheroSigueAlTipo() {
        assertEquals("nota.webm", AzureSpeechTranscriptor.nombreFichero("video/webm;codecs=opus"));
        assertEquals("nota.m4a", AzureSpeechTranscriptor.nombreFichero("AUDIO/MP4"));
        assertEquals("nota.ogg", AzureSpeechTranscriptor.nombreFichero("audio/ogg"));
        assertEquals("nota.wav", AzureSpeechTranscriptor.nombreFichero("audio/wav"));
        assertEquals("nota.mp3", AzureSpeechTranscriptor.nombreFichero("audio/mpeg"));
        assertEquals("nota.webm", AzureSpeechTranscriptor.nombreFichero(null));
    }

    @Test
    void estaDisponibleSoloConClaveYRegion() {
        RestClient.Builder builder = RestClient.builder();
        assertTrue(new AzureSpeechTranscriptor(builder, props("clave", "westeurope")).disponible());
        assertFalse(new AzureSpeechTranscriptor(builder, props("", "westeurope")).disponible());
        assertFalse(new AzureSpeechTranscriptor(builder, props("clave", " ")).disponible());
    }

    private static ModelosProperties props(String clave, String region) {
        return new ModelosProperties(null, null, List.of(), List.of(), null, null,
                new ModelosProperties.AzureSpeech(clave, region));
    }
}

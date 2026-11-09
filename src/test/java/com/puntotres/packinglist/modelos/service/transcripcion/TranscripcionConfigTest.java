package com.puntotres.packinglist.modelos.service.transcripcion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.puntotres.packinglist.modelos.ModelosProperties;

/**
 * Qué transcriptor y qué resumidor se usan: el de verdad si está configurado,
 * el apagado si no. Construirlos no llama a nada (los clientes son perezosos).
 */
class TranscripcionConfigTest {

    private final TranscripcionConfig config = new TranscripcionConfig();

    @Test
    void sinClaveDeAzureNoSeTranscribe() throws IOException {
        Transcriptor apagado = config.transcriptor(props("", "westeurope"), RestClient.builder());
        assertInstanceOf(TranscriptorDesactivado.class, apagado);
        assertFalse(apagado.disponible());
        assertThrows(IOException.class, () -> apagado.transcribir(new byte[] {1}, "audio/webm"));

        assertInstanceOf(AzureSpeechTranscriptor.class,
                config.transcriptor(props("clave", "westeurope"), RestClient.builder()));
    }

    @Test
    void sinClaveDeAnthropicNoSeResume() {
        ResumidorModificacion apagado = TranscripcionConfig.resumidor(props("", ""), Map.<String, String>of()::get);
        assertInstanceOf(ResumidorDesactivado.class, apagado);
        assertFalse(apagado.disponible());
        assertInstanceOf(ResumidorDesactivado.class,
                TranscripcionConfig.resumidor(props("", ""), Map.of("ANTHROPIC_API_KEY", " ")::get));

        ResumidorModificacion real = TranscripcionConfig.resumidor(props("", ""),
                Map.of("ANTHROPIC_API_KEY", "sk-prueba")::get);
        assertInstanceOf(ResumidorClaude.class, real);
        assertTrue(real.disponible());
    }

    @Test
    void conElResumenApagadoNoSeResumeAunqueHayaClave() {
        assertInstanceOf(ResumidorDesactivado.class, TranscripcionConfig.resumidor(props("", ""),
                Map.of("ANTHROPIC_API_KEY", "sk-prueba")::get, false));
    }

    private static ModelosProperties props(String clave, String region) {
        return new ModelosProperties(null, null, List.of(), List.of(), null, null,
                new ModelosProperties.AzureSpeech(clave, region));
    }
}

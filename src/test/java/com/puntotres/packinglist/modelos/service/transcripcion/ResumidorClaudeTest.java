package com.puntotres.packinglist.modelos.service.transcripcion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.puntotres.packinglist.modelos.ModelosProperties;

/**
 * El resumen de la IA sin red: la petición que se manda y cómo se lee la
 * respuesta. Ningún test llama a la API (cuesta dinero y no se puede repetir).
 */
class ResumidorClaudeTest {

    private static final ModelosProperties PROPS = new ModelosProperties(null, null,
            List.of(new ModelosProperties.TipoCambio("CONSUMO_PIEL", "Consumo piel", "piel"),
                    new ModelosProperties.TipoCambio("CONSTRUCCION", "Construcción", "costura")),
            List.of(), null, null, null);

    @Test
    void parsearLeeLosCincoCampos() throws IOException {
        ResumenIa resumen = ResumidorClaude.parsear("""
                {"titulo": "Costura del asa reforzada",
                 "resumen": "Se ha doblado la costura del asa porque se abría.",
                 "tipoCambioSugerido": "CONSTRUCCION",
                 "materiales": ["hilo encerado 0,8", "piel de vaca"],
                 "dudas": ["No se entiende si es el asa larga o la corta"]}""");

        assertEquals(new ResumenIa("Costura del asa reforzada", "Se ha doblado la costura del asa porque se abría.",
                "CONSTRUCCION", List.of("hilo encerado 0,8", "piel de vaca"),
                List.of("No se entiende si es el asa larga o la corta")), resumen);
    }

    @Test
    void sinListasLasDejaVaciasYSinTextosLosDejaEnBlanco() throws IOException {
        ResumenIa resumen = ResumidorClaude.parsear("{\"titulo\": \"  Forro nuevo \"}");

        assertEquals("Forro nuevo", resumen.titulo());
        assertEquals("", resumen.resumen());
        assertEquals("", resumen.tipoCambioSugerido());
        assertEquals(List.of(), resumen.materiales());
        assertEquals(List.of(), resumen.dudas());
    }

    @Test
    void unaRespuestaQueNoEsJsonEsUnError() {
        IOException error = assertThrows(IOException.class, () -> ResumidorClaude.parsear("lo siento"));
        assertTrue(error.getMessage().startsWith("La respuesta de la IA no se entiende"), error.getMessage());
    }

    @Test
    void laPeticionVaAOpusConRazonamientoAdaptativoEsfuerzoBajoYEsquema() {
        MessageCreateParams peticion = new ResumidorClaude(PROPS, Map.of("ANTHROPIC_API_KEY", "x")::get)
                .peticionPara("He reforzado el asa", "Construcción", "T16-ASA · Bolso T16");

        assertEquals("claude-opus-5-5", peticion.model().asString());
        assertTrue(peticion.thinking().orElseThrow().isAdaptive());
        OutputConfig salida = peticion.outputConfig().orElseThrow();
        assertEquals(OutputConfig.Effort.LOW, salida.effort().orElseThrow());
        assertTrue(salida.format().isPresent(), "la salida va con esquema JSON");
        String sistema = peticion.system().orElseThrow().asString();
        assertTrue(sistema.startsWith("Eres el asistente de despacho de un taller de marroquinería (Puntotres)."),
                sistema);
        assertTrue(sistema.contains("CONSUMO_PIEL, CONSTRUCCION"), "los tipos de cambio salen del yml: " + sistema);
        String usuario = peticion.messages().get(0).content().asString();
        assertTrue(usuario.contains("He reforzado el asa"), usuario);
        assertTrue(usuario.contains("T16-ASA · Bolso T16"), usuario);
        assertTrue(usuario.contains("Construcción"), usuario);
    }

    @Test
    void estaDisponibleSoloConLaClaveDeLaApi() {
        assertTrue(new ResumidorClaude(PROPS, Map.of("ANTHROPIC_API_KEY", "sk-algo")::get).disponible());
        assertFalse(new ResumidorClaude(PROPS, Map.of("ANTHROPIC_API_KEY", "  ")::get).disponible());
        assertFalse(new ResumidorClaude(PROPS, Map.<String, String>of()::get).disponible());
    }
}

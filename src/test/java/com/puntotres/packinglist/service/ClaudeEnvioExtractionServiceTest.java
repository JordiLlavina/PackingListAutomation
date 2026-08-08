package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.EnvioInput;

class ClaudeEnvioExtractionServiceTest {

    private final ClaudeEnvioExtractionService servicio =
            new ClaudeEnvioExtractionService(new ObjectMapper());

    // --- prompt por plantilla ---

    @Test
    void elPromptComparteElNucleoYCambiaElBloquePorPlantilla() {
        String ami = ClaudeEnvioExtractionService.promptPara(TipoPlantilla.AMI);
        String apc = ClaudeEnvioExtractionService.promptPara(TipoPlantilla.APC);
        String generico = ClaudeEnvioExtractionService.promptPara(TipoPlantilla.GENERIC);

        // El núcleo (taquigrafía del operario) está en los tres.
        for (String prompt : List.of(ami, apc, generico)) {
            assertTrue(prompt.contains("Lo TACHADO no existe"));
            assertTrue(prompt.contains("apóstrofo"));
            assertTrue(prompt.contains("resumenPalets"));
            assertTrue(prompt.contains("TODO Nº"));
        }
        // Y el bloque de cliente solo en el suyo.
        assertTrue(ami.contains("Este envío es de AMI"));
        assertTrue(ami.contains("DOS primeros grupos"));
        assertTrue(apc.contains("Este envío es de APC"));
        assertTrue(apc.contains("TRES ÚLTIMOS DÍGITOS"));
        assertTrue(generico.contains("plantilla genérica"));
        assertFalse(ami.contains("TRES ÚLTIMOS DÍGITOS"));
        assertFalse(apc.contains("DOS primeros grupos"));
        assertFalse(generico.contains("Este envío es de AMI"));
    }

    /**
     * La barra que separa referencia / modelo / color en la cabecera es una
     * convención NUEVA del operario, así que el prompt tiene que decir dos
     * cosas o crea más fallos de los que quita: que solo vale en esa línea
     * (la barra ya significa "unidades/talla" en las líneas de caja y separa
     * filas en la tabla de palets) y que sin ella se sigue leyendo con las
     * reglas de siempre (las hojas ya escritas no la llevan).
     */
    @Test
    void elPromptEnsenaLaBarraDeLaCabeceraSinHacerlaObligatoriaNiGlobal() {
        for (TipoPlantilla plantilla : TipoPlantilla.values()) {
            String prompt = ClaudeEnvioExtractionService.promptPara(plantilla);
            assertTrue(prompt.contains("BARRA"), plantilla + ": no enseña la barra");
            assertTrue(prompt.contains("122/75"),
                    plantilla + ": no acota la barra a la línea de cabecera");
            assertTrue(prompt.contains("Cuando NO está"),
                    plantilla + ": sin la barra hay que seguir leyendo la cabecera");
        }
    }

    @Test
    void elPromptDeApcProhibeElLivraisonCodeYMapeaLasAbreviaturas() {
        String apc = ClaudeEnvioExtractionService.promptPara(TipoPlantilla.APC);
        assertTrue(apc.contains("livraisonCode"));
        assertTrue(apc.contains("Wh. / Wholesale -> WHOLESALE"));
        assertTrue(apc.contains("REINICIAN EN 1"));
        String ami = ClaudeEnvioExtractionService.promptPara(TipoPlantilla.AMI);
        assertTrue(ami.contains("CONTINUOS"));
    }

    // --- adjuntos: PDF como documento, imagen como imagen ---

    @Test
    void unPdfViajaComoDocumentoYUnaImagenComoImagen() {
        List<ContentBlockParam> bloques = ClaudeEnvioExtractionService.bloquesDe(List.of(
                new ClaudeEnvioExtractionService.Adjunto("application/pdf", new byte[] {1}),
                new ClaudeEnvioExtractionService.Adjunto("image/jpeg", new byte[] {2})));
        // Cada adjunto va precedido de su ordinal: texto, pdf, texto, imagen.
        assertEquals(4, bloques.size());
        assertTrue(bloques.get(0).isText());
        assertTrue(bloques.get(1).isDocument());
        assertTrue(bloques.get(2).isText());
        assertTrue(bloques.get(3).isImage());
    }

    @Test
    void elRazonamientoEsAdaptativoConEsfuerzoYTechoDeSobra() {
        // El razonamiento y el JSON comparten el techo de max_tokens, y
        // descifrar 7 páginas de caligrafía consume mucho razonamiento: con
        // el techo de 16.000 que había antes, la respuesta llegaba cortada
        // sin JSON. El techo tiene que ser holgado.
        //
        // Y el razonamiento se controla con output_config.effort, NUNCA con
        // thinking.enabled/budgetTokens: Opus 4.8 lo rechaza con un 400
        // ("thinking.type.enabled is not supported for this model"), que es
        // un fallo en tiempo de ejecución que ningún test de dominio ve.
        MessageCreateParams peticion = ClaudeEnvioExtractionService.peticionPara(
                List.of(new ClaudeEnvioExtractionService.Adjunto(
                        "application/pdf", new byte[] {1})),
                TipoPlantilla.APC);

        assertTrue(peticion.thinking().orElseThrow().isAdaptive(),
                "Opus 4.8 solo admite thinking adaptativo");
        assertTrue(peticion.outputConfig().orElseThrow().effort().isPresent(),
                "sin effort no hay forma de acotar cuánto razona");
        assertTrue(peticion.maxTokens() >= 24000,
                "con el techo justo, el razonamiento se come el JSON");
    }

    @Test
    void unFormatoNoSoportadoExplicaCualesValen() {
        ClaudeEnvioExtractionService.ExtraccionException error = assertThrows(
                ClaudeEnvioExtractionService.ExtraccionException.class,
                () -> ClaudeEnvioExtractionService.bloquesDe(List.of(
                        new ClaudeEnvioExtractionService.Adjunto("image/tiff", new byte[] {1}))));
        assertTrue(error.getMessage().contains("PDF"));
        assertTrue(error.getMessage().contains("image/tiff"));
    }

    // --- parseo de la respuesta ---

    @Test
    void elApostrofoDecimalDelOperarioSeNormalizaAntesDeJackson() {
        // Regla 5 del prompt con red de seguridad: si el modelo copiara
        // "13'52" tal cual, el JSON entero moriría sin diagnóstico.
        EnvioInput envio = servicio.parsear("""
                {"destinos": [{"destino": "PARIS", "referencias": [
                  {"referencia": "UBL029.AL0216", "color": "2221", "medidaCaja": "60x40x30",
                   "pedido": "07672", "cajas": [{"caja": 1, "unidades": 61, "pesoBruto": 13'52}]}
                ]}]}""");
        assertEquals(13.52,
                envio.getDestinos().get(0).getReferencias().get(0).getCajas().get(0).getPesoBruto(),
                0.001);
    }

    @Test
    void elJsonSeAislaAunqueVengaEnValladoMarkdown() {
        EnvioInput envio = servicio.parsear(
                "```json\n{\"cliente\": \"AMI\", \"destinos\": []}\n```");
        assertEquals("AMI", envio.getCliente());
    }

    @Test
    void unaRespuestaSinJsonExplicaElProblema() {
        assertThrows(ClaudeEnvioExtractionService.ExtraccionException.class,
                () -> servicio.parsear("No he podido leer las hojas"));
    }

    @Test
    void losCamposNuevosDeLaExtraccionSeDeserializan() {
        EnvioInput envio = servicio.parsear("""
                {"avisos": ["duda en la caja 6"],
                 "resumenPalets": [{"destino": "WHOLESALE", "palets": 5}],
                 "destinos": []}""");
        assertEquals(List.of("duda en la caja 6"), envio.getAvisos());
        assertEquals("WHOLESALE", envio.getResumenPalets().get(0).getDestino());
        assertEquals(5, envio.getResumenPalets().get(0).getPalets());
    }
}

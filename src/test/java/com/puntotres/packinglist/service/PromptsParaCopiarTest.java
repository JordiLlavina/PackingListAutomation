package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.TipoPlantilla;

/**
 * docs/Packing Lists/prompts-para-copiar.md es el plan B cuando la API falla:
 * el usuario copia esos prompts a claude.ai y pega el JSON en la aplicación.
 * Si el documento se queda viejo, el camino manual usa un prompt distinto del
 * de la aplicación y los dos dan resultados distintos <b>sin que nada avise</b>
 * — un fallo invisible desde el código. Este test ancla que sean idénticos.
 */
class PromptsParaCopiarTest {

    private static final Path DOC = Path.of("docs", "Packing Lists", "prompts-para-copiar.md");

    private static String documento() throws IOException {
        assertTrue(Files.exists(DOC), "falta " + DOC);
        return Files.readString(DOC);
    }

    /** El texto entre los marcadores de un bloque, sin la valla ```text. */
    private static String bloque(String documento, String nombre) {
        String inicio = "<!-- prompt:" + nombre + " -->";
        String fin = "<!-- /prompt:" + nombre + " -->";
        int desde = documento.indexOf(inicio);
        int hasta = documento.indexOf(fin);
        assertTrue(desde >= 0 && hasta > desde, "falta el bloque " + nombre + " en " + DOC);
        return documento.substring(desde + inicio.length(), hasta)
                .replace("```text", "")
                .replace("```", "")
                .strip();
    }

    @Test
    void elDocumentoTraeElPromptDeCadaClienteTalCualLoMandaLaAplicacion() throws IOException {
        String documento = documento();
        for (TipoPlantilla plantilla : TipoPlantilla.values()) {
            assertEquals(ClaudeEnvioExtractionService.promptPara(plantilla).strip(),
                    bloque(documento, plantilla.name()),
                    "el prompt de " + plantilla + " en " + DOC + " no coincide con el código: "
                            + "regenéralo o el uso manual dará otro resultado");
        }
    }

    @Test
    void elDocumentoTraeTambienElMensajeFinal() throws IOException {
        assertEquals(ClaudeEnvioExtractionService.MENSAJE_USUARIO.strip(),
                bloque(documento(), "MENSAJE_USUARIO"));
    }
}

package com.puntotres.packinglist.service.escandallos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Regla del proyecto: un fichero que no se puede leer no puede tumbar el
 * proceso entero, porque los demás sí se pueden volcar.
 */
class EscandallosGenerationServiceTest {

    private final EscandallosGenerationService service =
            new EscandallosGenerationService(new EscandalloReader(), new EscandallosExcelBuilder());

    private static FicheroEscandallo recurso(String nombre) throws Exception {
        String ruta = "/ejemplos/escandallos/" + nombre;
        try (InputStream entrada = EscandallosGenerationServiceTest.class
                .getResourceAsStream(ruta)) {
            assertNotNull(entrada, "falta el recurso de test " + ruta);
            return new FicheroEscandallo(nombre, entrada.readAllBytes());
        }
    }

    private static FicheroEscandallo basura(String nombre) {
        return new FicheroEscandallo(nombre, "esto no es un excel".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void losDosEscandallosRealesSalenEnDosHojasYSinAvisos() throws Exception {
        ExcelEscandallos excel = service.procesar(
                List.of(recurso("ULL770 NOIR.xlsx"), recurso("ULL770 SAND.xlsx")));

        assertEquals(List.of("ULL770.AL245 NOIR", "ULL770.AL245 SABLE SAND"), excel.hojas());
        assertEquals(List.of(), excel.avisos());
        assertFalse(excel.estaVacio());
    }

    @Test
    void lasHojasVanEnElOrdenEnQueSeSubieronLosFicheros() throws Exception {
        ExcelEscandallos excel = service.procesar(
                List.of(recurso("ULL770 SAND.xlsx"), recurso("ULL770 NOIR.xlsx")));

        assertEquals(List.of("ULL770.AL245 SABLE SAND", "ULL770.AL245 NOIR"), excel.hojas());
    }

    @Test
    void unFicheroIlegibleSeOmiteConAvisoYElRestoSeProcesaIgual() throws Exception {
        ExcelEscandallos excel = service.procesar(
                List.of(recurso("ULL770 NOIR.xlsx"), basura("roto.xlsx"),
                        recurso("ULL770 SAND.xlsx")));

        assertEquals(List.of("ULL770.AL245 NOIR", "ULL770.AL245 SABLE SAND"), excel.hojas());
        assertTrue(excel.avisos().stream().anyMatch(aviso -> aviso.contains("roto.xlsx")),
                excel.avisos().toString());
    }

    @Test
    void siNingunFicheroEsUtilizableNoHayExcelQueDescargar() {
        ExcelEscandallos excel = service.procesar(List.of(basura("uno.xlsx"), basura("dos.xlsx")));

        assertTrue(excel.estaVacio());
        assertNull(excel.contenido());
        assertEquals(2, excel.avisos().size(), excel.avisos().toString());
    }

    @Test
    void sinFicherosNoHayNadaQueProcesar() {
        ExcelEscandallos excel = service.procesar(List.of());

        assertTrue(excel.estaVacio());
    }
}

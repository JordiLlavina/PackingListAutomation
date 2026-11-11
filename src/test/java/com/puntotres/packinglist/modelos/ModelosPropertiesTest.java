package com.puntotres.packinglist.modelos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Comprueba que el bloque {@code modelos} de application.yml se enlaza en
 * {@link ModelosProperties}: los botones de la cara del taller, los destinos
 * de aplicación y el aviso de que, sin variables de entorno, ni el push ni la
 * transcripción están configurados.
 */
@SpringBootTest
class ModelosPropertiesTest {

    @Autowired
    private ModelosProperties modelos;

    @Test
    void elYmlTraeLasOchoRazonesDeCambioEnSuOrdenYLosDosDestinos() {
        assertEquals(
                List.of("MEDIDAS_PATRONES", "CONSTRUCCION", "MANO_OBRA", "CONSUMO_PIEL", "REFUERZOS", "FORNITURAS",
                        "TROQUELES", "OTROS"),
                modelos.tiposCambio().stream().map(ModelosProperties.TipoCambio::clave).toList());
        ModelosProperties.TipoCambio herrajes = modelos.tipoCambio("FORNITURAS").orElseThrow();
        assertEquals("Fornituras", herrajes.texto());
        assertEquals("herraje", herrajes.icono());
        assertEquals("Consumo piel · Fornituras", modelos.textoTiposCambio("CONSUMO_PIEL,FORNITURAS"),
                "varias razones salen juntas");
        assertEquals("Consumo piel · VIEJA", modelos.textoTiposCambio(" CONSUMO_PIEL , VIEJA,, "),
                "una clave que ya no está en el yml sale tal cual");
        assertNull(modelos.textoTiposCambio(null));
        assertNull(modelos.textoTiposCambio(" , "));
        assertEquals(List.of("A", "B"), ModelosProperties.clavesDe("A, B,A"));
        assertEquals("Consumo piel", modelos.tipoCambio("CONSUMO_PIEL").orElseThrow().texto());
        assertTrue(modelos.tipoCambio("NO_EXISTE").isEmpty());
        assertTrue(modelos.tipoCambio(null).isEmpty());
        assertEquals(List.of("ICSUITE", "EXCEL_ESCANDALLOS"), modelos.destinosAplicacion());
        assertEquals(-1, modelos.recordatorios().taller().resumenColaHora());
        assertEquals(1, modelos.recordatorios().taller().urgenteDias());
        assertEquals(7, modelos.recordatorios().despacho().sinAplicarDias());
        assertEquals(24, modelos.recordatorios().despacho().respuestaPendienteHoras());
    }

    @Test
    void sinVariablesNiPushNiTranscripcionEstanConfigurados() {
        assertFalse(modelos.pushConfigurado());
        assertFalse(modelos.transcripcionConfigurada());
        assertEquals("westeurope", modelos.azureSpeech().region());
    }

    @Test
    void laUrlDelModeloEsLaPublicaMasM() {
        String url = modelos.urlDeModelo(12);
        assertTrue(url.endsWith("/m/12"), url);
        assertFalse(url.contains("//m"), url);
        ModelosProperties conBarra = new ModelosProperties("https://modelos.puntotres.com/", "", List.of(),
                List.of(), null, null, null);
        assertEquals("https://modelos.puntotres.com/m/12", conBarra.urlDeModelo(12));
    }
}

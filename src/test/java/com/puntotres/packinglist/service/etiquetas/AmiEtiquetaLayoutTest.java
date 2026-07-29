package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Ancla las coordenadas medidas en client-labels/ami-etiquetas-template.xlsx.
 * Si un test de aquí falla es que alguien ha tocado la plantilla o el layout
 * sin el otro: comparar contra docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx.
 */
class AmiEtiquetaLayoutTest {

    @Test
    void chinaTieneLosTresCodigosEnSuSitio() {
        assertEquals(new AnclajeBloque(8, 2971800, 19050, 1047750, 666750),
                AmiEtiquetaLayout.CHINA.po());
        assertEquals(new AnclajeBloque(10, 2613660, 162388, 1478280, 652951),
                AmiEtiquetaLayout.CHINA.ean13());
        assertEquals(new AnclajeBloque(12, 1394460, 420424, 2727960, 455876),
                AmiEtiquetaLayout.CHINA.ean128());
    }

    @Test
    void japanTieneLosTresCodigosEnSuSitioYLaDireccion() {
        assertEquals(new AnclajeBloque(7, 2857500, 19050, 990600, 628650),
                AmiEtiquetaLayout.JAPAN.po());
        assertEquals(new AnclajeBloque(9, 2430780, 68580, 1478280, 652951),
                AmiEtiquetaLayout.JAPAN.ean13());
        assertEquals(new AnclajeBloque(12, 899160, 15168, 3009900, 502991),
                AmiEtiquetaLayout.JAPAN.ean128());
        assertEquals(new AnclajeBloque(2, 66675, 95250, 2562225, 1143000),
                AmiEtiquetaLayout.JAPAN_DIRECCION);
    }

    @Test
    void franceTieneLosTresCodigosEnSuSitio() {
        assertEquals(new AnclajeBloque(7, 3457575, 9525, 1209675, 762000),
                AmiEtiquetaLayout.FRANCE.po());
        assertEquals(new AnclajeBloque(9, 3116580, 68580, 1569902, 693420),
                AmiEtiquetaLayout.FRANCE.ean13());
        assertEquals(new AnclajeBloque(11, 2095500, 423031, 2575560, 430408),
                AmiEtiquetaLayout.FRANCE.ean128());
    }

    @Test
    void todosLosCodigosCabenDentroDeSuBloque() {
        // Las imágenes se replican a +offsetSegundaEtiqueta, así que ninguna
        // puede arrancar más allá de esa mitad o pisaría la etiqueta de abajo.
        for (AmiEtiquetaLayout layout : new AmiEtiquetaLayout[] {
                AmiEtiquetaLayout.CHINA, AmiEtiquetaLayout.JAPAN, AmiEtiquetaLayout.FRANCE }) {
            for (AnclajeBloque anclaje : new AnclajeBloque[] {
                    layout.po(), layout.ean13(), layout.ean128() }) {
                assertTrue(anclaje.fila() < layout.offsetSegundaEtiqueta(),
                        layout.nombreHoja() + ": el anclaje de la fila " + anclaje.fila()
                                + " se sale de la primera etiqueta del par");
            }
        }
    }
}

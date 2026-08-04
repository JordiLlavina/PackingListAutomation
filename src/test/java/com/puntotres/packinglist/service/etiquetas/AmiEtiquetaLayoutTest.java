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
    void chinaTieneLaImagenYElEan128EnSuSitio() {
        assertEquals(new AnclajeBloque(10, 2481943, 54429, 1674091, 762000),
                AmiEtiquetaLayout.CHINA.imagenArticulo());
        assertEquals(new AnclajeBloque(8, 1352897, 143333, 2727960, 452413),
                AmiEtiquetaLayout.CHINA.ean128());
    }

    @Test
    void japanTieneLaImagenElEan128YLaDireccion() {
        assertEquals(new AnclajeBloque(9, 2241177, 26896, 1674091, 762000),
                AmiEtiquetaLayout.JAPAN.imagenArticulo());
        assertEquals(new AnclajeBloque(7, 918884, 62682, 3009900, 502991),
                AmiEtiquetaLayout.JAPAN.ean128());
        assertEquals(new AnclajeBloque(2, 66675, 95250, 2562225, 1143000),
                AmiEtiquetaLayout.JAPAN_DIRECCION);
    }

    @Test
    void franceTieneLaImagenYElEan128EnSuSitio() {
        assertEquals(new AnclajeBloque(9, 2937163, 69273, 1674091, 762000),
                AmiEtiquetaLayout.FRANCE.imagenArticulo());
        assertEquals(new AnclajeBloque(7, 2057400, 156331, 2575560, 430408),
                AmiEtiquetaLayout.FRANCE.ean128());
    }

    @Test
    void elHuecoDeLaImagenEsElMismoEnLasTresDestinaciones() {
        // La imagen compuesta se genera con la proporción del hueco: si una
        // destinación tuviera otra, saldría deformada en esa.
        for (AmiEtiquetaLayout layout : todos()) {
            assertEquals(1674091, layout.imagenArticulo().cx(), layout.nombreHoja());
            assertEquals(762000, layout.imagenArticulo().cy(), layout.nombreHoja());
        }
    }

    @Test
    void lasFilasDeValorSonLasDeLaPlantillaNueva() {
        assertEquals(8, AmiEtiquetaLayout.CHINA.filaOrderNumber());
        assertEquals(10, AmiEtiquetaLayout.CHINA.filaReferencia());
        assertEquals(7, AmiEtiquetaLayout.JAPAN.filaOrderNumber());
        assertEquals(9, AmiEtiquetaLayout.JAPAN.filaReferencia());
        assertEquals(7, AmiEtiquetaLayout.FRANCE.filaOrderNumber());
        assertEquals(9, AmiEtiquetaLayout.FRANCE.filaReferencia());
    }

    @Test
    void todasLasImagenesCabenDentroDeSuBloque() {
        // Las imágenes se replican a +offsetSegundaEtiqueta, así que ninguna
        // puede arrancar más allá de esa mitad o pisaría la etiqueta de abajo.
        for (AmiEtiquetaLayout layout : todos()) {
            for (AnclajeBloque anclaje : new AnclajeBloque[] {
                    layout.imagenArticulo(), layout.ean128() }) {
                assertTrue(anclaje.fila() < layout.offsetSegundaEtiqueta(),
                        layout.nombreHoja() + ": el anclaje de la fila " + anclaje.fila()
                                + " se sale de la primera etiqueta del par");
            }
        }
    }

    private static AmiEtiquetaLayout[] todos() {
        return new AmiEtiquetaLayout[] {
                AmiEtiquetaLayout.CHINA, AmiEtiquetaLayout.JAPAN, AmiEtiquetaLayout.FRANCE };
    }
}

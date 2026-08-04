package com.puntotres.packinglist.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TaraPropertiesTest {

    @Test
    void normalizaMayusculasYEspaciosAlBuscarLaTara() {
        TaraProperties props = new TaraProperties();
        props.setTaras(Map.of("60x40x40", 1.6));

        assertEquals(1.6, props.taraPara(" 60X40X40 ").get());
        assertEquals(1.6, props.taraPara("60 x 40 x 40").get());
    }

    @Test
    void tamanoDesconocidoDevuelveVacio() {
        TaraProperties props = new TaraProperties();
        props.setTaras(Map.of("60x40x40", 1.6));

        assertTrue(props.taraPara("99x99x99").isEmpty());
    }

    @Test
    void tamanoNuloDevuelveVacio() {
        TaraProperties props = new TaraProperties();
        props.setTaras(Map.of("60x40x40", 1.6));

        assertTrue(props.taraPara(null).isEmpty());
    }

    @Test
    void losTamanosSeOrdenanDeMayorAMenorPorVolumen() {
        TaraProperties props = new TaraProperties();
        props.setTaras(Map.of("60x40x40", 0.6, "40x30x20", 0.2,
                "100x40x40", 1.0, "60x40x30", 0.2));

        // Por volumen, no alfabéticamente: "100x40x40" es la caja más grande
        // pero como texto va antes que "40x30x20".
        assertEquals(List.of("100x40x40", "60x40x40", "60x40x30", "40x30x20"),
                props.tamanosDeMayorAMenor());
    }

    @Test
    void unTamanoQueNoEsLxAxHVaAlFinalEnVezDeRomperElOrden() {
        TaraProperties props = new TaraProperties();
        props.setTaras(Map.of("60x40x40", 0.6, "caja grande", 1.0, "40x30x20", 0.2));

        assertEquals(List.of("60x40x40", "40x30x20", "cajagrande"),
                props.tamanosDeMayorAMenor());
    }

    @Test
    void setTarasReemplazaLaTablaAnteriorEnVezDeAcumular() {
        TaraProperties props = new TaraProperties();
        props.setTaras(Map.of("60x40x40", 1.6));

        props.setTaras(Map.of("60x40x30", 1.2));

        assertTrue(props.taraPara("60x40x40").isEmpty());
        assertEquals(1.2, props.taraPara("60x40x30").get());
    }
}

package com.puntotres.packinglist.service.escandallos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * El campo COLOR del ERP trae delante sus códigos internos («000    0014 NOIR»)
 * y en el excel de escandallos solo se quiere la descripción.
 */
class EscandalloTest {

    private static Escandallo conColor(String color) {
        return new Escandallo("ULL770.AL245", "BOLSO", color, List.of(), "x.xlsx");
    }

    @Test
    void seQuitanLosCodigosDeLosDosEscandallosReales() {
        assertEquals("NOIR", conColor("000    0014 NOIR").descripcionColor());
        assertEquals("SABLE SAND", conColor("001    255 SABLE SAND").descripcionColor());
    }

    /** AMI tiene códigos de color que empiezan por letra, como «A237». */
    @Test
    void unCodigoDeColorConLetrasTambienSeQuita() {
        assertEquals("DARK COFFEE", conColor("000 A237 DARK COFFEE").descripcionColor());
    }

    @Test
    void unNumeroDentroDeLaDescripcionSeQueda() {
        assertEquals("BEIGE 2 TONOS", conColor("000 0014 BEIGE 2 TONOS").descripcionColor());
    }

    @Test
    void unColorSinDescripcionSeQuedaConSusCodigos() {
        assertEquals("000 0014", conColor("000    0014").descripcionColor());
    }

    @Test
    void sinColorNoHayDescripcion() {
        assertEquals("", conColor(null).descripcionColor());
        assertEquals("", conColor("   ").descripcionColor());
    }
}

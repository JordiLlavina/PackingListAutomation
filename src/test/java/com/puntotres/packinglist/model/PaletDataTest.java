package com.puntotres.packinglist.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaletDataTest {

    @Test
    void contieneEsInclusivoEnAmbosExtremosDelRango() {
        PaletData palet = new PaletData();
        palet.setCajaInicio(10);
        palet.setCajaFin(20);

        assertTrue(palet.contiene(10));
        assertTrue(palet.contiene(20));
        assertTrue(palet.contiene(15));
    }

    @Test
    void noContieneNumerosFueraDelRangoPorNingunLado() {
        PaletData palet = new PaletData();
        palet.setCajaInicio(10);
        palet.setCajaFin(20);

        assertFalse(palet.contiene(9));
        assertFalse(palet.contiene(21));
    }
}

package com.puntotres.packinglist.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CajaDataTest {

    @Test
    void tienePesosCompletosSoloCuandoNetoYBrutoNoSonNulos() {
        CajaData caja = new CajaData();
        assertFalse(caja.tienePesosCompletos());

        caja.setPesoNetoKg(10.0);
        assertFalse(caja.tienePesosCompletos()); // falta el bruto

        caja.setPesoBrutoKg(11.6);
        assertTrue(caja.tienePesosCompletos());

        caja.setPesoNetoKg(null);
        assertFalse(caja.tienePesosCompletos()); // falta el neto
    }
}

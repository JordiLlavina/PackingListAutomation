package com.puntotres.packinglist.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import static com.puntotres.packinglist.testutil.TestDatos.caja;

/**
 * La caja física es la unidad de peso del envío: una caja de cartón que
 * puede ocupar varias líneas del JSON (varias tallas, colores o
 * referencias) pero se pesa UNA sola vez, en su primera línea.
 */
class CajaFisicaTest {

    @Test
    void agrupaLasLineasPorNumeroDeCajaConservandoElOrden() {
        CajaData primeraDeLa1 = caja(1, "PO", "REF-A", "NEGRO", 5, 4.4, 5.0);
        CajaData segundaDeLa1 = caja(1, "PO", "REF-B", "NEGRO", 3, null, null);
        CajaData unicaDeLa2 = caja(2, "PO", "REF-A", "NEGRO", 8, 7.4, 8.0);

        List<CajaFisica> cajas = CajaFisica.agrupar(
                List.of(primeraDeLa1, unicaDeLa2, segundaDeLa1));

        assertEquals(2, cajas.size());
        assertEquals(1, cajas.get(0).numeroCaja());
        assertEquals(List.of(primeraDeLa1, segundaDeLa1), cajas.get(0).lineas());
        assertEquals(2, cajas.get(1).numeroCaja());
        assertSame(primeraDeLa1, cajas.get(0).lider());
    }

    @Test
    void elPesoDeLaCajaEsElDeSuLineaLider() {
        CajaFisica mixta = CajaFisica.de(List.of(
                caja(7, "PO", "REF-A", "NEGRO", 5, 4.4, 5.0),
                caja(7, "PO", "REF-A", "NEGRO", 3, null, null)));

        assertEquals(5.0, mixta.pesoBrutoKg());
        assertEquals(4.4, mixta.pesoNetoKg());
        assertTrue(mixta.tienePesosCompletos());
    }

    @Test
    void sinPesoEnLaLiderLaCajaNoTienePesoAunqueOtraLineaLoTraiga() {
        CajaFisica mixta = CajaFisica.de(List.of(
                caja(7, "PO", "REF-A", "NEGRO", 5, null, null),
                caja(7, "PO", "REF-A", "NEGRO", 3, 2.8, 3.0)));

        assertNull(mixta.pesoBrutoKg());
        assertFalse(mixta.tienePesosCompletos());
    }

    @Test
    void elPaletDeLaCajaEsElDeSuLider() {
        CajaData lider = caja(7, "PO", "REF-A", "NEGRO", 5, 4.4, 5.0);
        lider.setNumeroPalet(3);

        assertEquals(3, CajaFisica.de(List.of(lider)).numeroPalet());
    }
}

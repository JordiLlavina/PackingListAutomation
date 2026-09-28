package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReferenciaCorteTest {

    @Test
    void amiLlevaLaPielDetrasDelPunto() {
        ReferenciaCorte referencia = ReferenciaCorte.deAmi("ULL712.AL0103");

        assertEquals("ULL712.AL0103", referencia.referencia());
        assertEquals("ULL712", referencia.modelo());
        assertEquals("AL0103", referencia.piel());
        assertTrue(referencia.tienePiel());
    }

    @Test
    void apcLlevaLaPielDelanteDelGuion() {
        ReferenciaCorte referencia = ReferenciaCorte.deApc("PXCBC-F67008");

        assertEquals("F67008", referencia.modelo());
        assertEquals("PXCBC", referencia.piel());
    }

    @Test
    void seNormalizaComoLlegue() {
        ReferenciaCorte referencia = ReferenciaCorte.deAmi("  ull712.al0103 ");

        assertEquals("ULL712.AL0103", referencia.referencia());
        assertEquals("ULL712", referencia.modelo());
    }

    @Test
    void sinSeparadorLaReferenciaEsElModeloYNoSeAdivinaLaPiel() {
        ReferenciaCorte ami = ReferenciaCorte.deAmi("ULL712");
        ReferenciaCorte apc = ReferenciaCorte.deApc("F67008");
        ReferenciaCorte puntoAlPrincipio = ReferenciaCorte.deAmi(".AL0103");

        assertEquals("ULL712", ami.modelo());
        assertFalse(ami.tienePiel());
        assertEquals("F67008", apc.modelo());
        assertFalse(apc.tienePiel());
        assertEquals(".AL0103", puntoAlPrincipio.modelo());
        assertFalse(puntoAlPrincipio.tienePiel());
    }
}

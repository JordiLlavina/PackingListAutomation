package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * La referencia de APC es REF.PIEL-REF.ARTÍCULO ("PXCBC-F67008"), y la
 * operaria a veces la escribe al revés ("F67008-PXCBC").
 */
class ReferenciaApcTest {

    @Test
    void unaReferenciaAlRevesSeDaLaVuelta() {
        assertEquals("PXCBC-F67008", ReferenciaApc.normalizar("F67008-PXCBC"));
        assertEquals("COHIZ-F67008", ReferenciaApc.normalizar("F67008-COHIZ"));
    }

    @Test
    void seLePerdonanMinusculasEspaciosYElGuionQueFalta() {
        assertEquals("PXCBC-F67008", ReferenciaApc.normalizar(" f67008 - pxcbc "));
        assertEquals("PXCBC-F67008", ReferenciaApc.normalizar("F67008 PXCBC"));
        assertEquals("PXCBC-67043", ReferenciaApc.normalizar("67043-PXCBC"));
    }

    @Test
    void unaReferenciaEnSuOrdenNoSeToca() {
        assertEquals("PXCBC-F67008", ReferenciaApc.normalizar("PXCBC-F67008"));
    }

    /**
     * Lo que no tiene la forma artículo + piel se deja como llegó: un recorte
     * ("67043"), las siglas sueltas que el operario pone detrás ("SJ", "SS",
     * que no son una piel) o una referencia de otro cliente.
     */
    @Test
    void loQueNoEsUnaReferenciaAlRevesSeDejaComoLlego() {
        assertEquals("67043", ReferenciaApc.normalizar("67043"));
        assertEquals("F63023", ReferenciaApc.normalizar("F63023"));
        assertEquals("67043 SJ", ReferenciaApc.normalizar("67043 SJ"));
        assertEquals("UBL029.AL0216", ReferenciaApc.normalizar("UBL029.AL0216"));
        assertEquals("BAG-A", ReferenciaApc.normalizar("BAG-A"));
        assertNull(ReferenciaApc.normalizar(null));
    }
}

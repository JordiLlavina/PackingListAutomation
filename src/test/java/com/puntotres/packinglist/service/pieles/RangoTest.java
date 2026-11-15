package com.puntotres.packinglist.service.pieles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** El grueso y la superficie se teclean en una casilla: un valor o un rango. */
class RangoTest {

    @Test
    void unValorSoloEsMinimoIgualAMaximo() {
        Rango rango = Rango.de("1,2");

        assertEquals(1.2, rango.minimo());
        assertEquals(1.2, rango.maximo());
        assertEquals("1,2", rango.texto());
    }

    @Test
    void unRangoAdmiteComaPuntoGuionEspaciosYLaUnidad() {
        assertEquals(new Rango(1.1, 1.3), Rango.de("1,1-1,3"));
        assertEquals(new Rango(1.1, 1.3), Rango.de(" 1.1 - 1.3 mm "));
        assertEquals(new Rango(4.0, 5.0), Rango.de("4 a 5 m2"));
        assertEquals("1,1-1,3", Rango.de("1.1 – 1.3").texto());
    }

    @Test
    void unRangoEscritoAlRevesSeOrdena() {
        assertEquals(new Rango(1.1, 1.3), Rango.de("1,3-1,1"));
    }

    @Test
    void vacioEsNoSeSabeYNoUnCero() {
        assertTrue(Rango.de("").vacio());
        assertTrue(Rango.de(null).vacio());
        assertEquals("", Rango.VACIO.texto());
    }

    @Test
    void loQueNoEsUnNumeroSeRechazaDiciendoComoEscribirlo() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Rango.de("fino"));

        assertTrue(error.getMessage().contains("fino"));
        assertTrue(error.getMessage().contains("1,1-1,3"));
        assertThrows(IllegalArgumentException.class, () -> Rango.de("1,1-"));
        assertThrows(IllegalArgumentException.class, () -> Rango.de("-1"));
    }

    @Test
    void elFiltroDeGruesoCogeLasPielesQueSolapanConLoPedido() {
        Rango piel = new Rango(1.1, 1.3);

        assertTrue(piel.solapa(1.2, 1.2), "un valor dentro del rango");
        assertTrue(piel.solapa(1.3, 1.6), "se tocan en el extremo");
        assertTrue(piel.solapa(null, 1.1));
        assertFalse(piel.solapa(1.4, 1.6));
        assertFalse(Rango.VACIO.solapa(1.0, 2.0), "de una piel sin grueso no se sabe: no pasa el filtro");
    }

    @Test
    void losNumerosSeEnseñanConComaYSinCerosDeSobra() {
        assertEquals("42", Rango.formato(42.0));
        assertEquals("42,5", Rango.formato(42.5));
        assertEquals("0,85", Rango.formato(0.85));
        assertEquals("", Rango.formato(null));
    }
}

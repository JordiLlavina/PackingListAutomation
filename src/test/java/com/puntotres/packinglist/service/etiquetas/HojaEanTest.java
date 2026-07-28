package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

class HojaEanTest {

    /** PO numérico (7672) para comprobar que no sale como "7672.0". */
    private static byte[] pedido() {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672));
    }

    @Test
    void localizaLaHojaEanAunqueNoSeaLaPrimera() throws Exception {
        // PedidoAmiExcel pone delante una hoja señuelo "BOLSITAS ANTIHUMEDAD".
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertEquals("EAN H26", hoja.nombre());
        }
    }

    @Test
    void resuelveLasColumnasPorElTextoDeLaCabecera() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertEquals(0, hoja.columna("MADE IN"));
            assertEquals(1, hoja.columna("ARTICLE"));
            assertEquals(2, hoja.columna("COLORIS"));
            // "Libellé coloris": se busca por "LIBELL" para no depender del acento.
            assertEquals(3, hoja.columna("LIBELL"));
            assertEquals(5, hoja.columna("TAILLE"));
            assertEquals(6, hoja.columna("PO"));
            assertEquals(8, hoja.columna("EAN13"));
        }
    }

    @Test
    void ean13NoSeConfundeConEan128() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertNotEquals(hoja.columna("EAN13"), hoja.columna("EAN128"));
        }
    }

    @Test
    void losNumerosSeLeenComoEnterosNoComoDecimales() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertEquals("7672", hoja.texto(hoja.primeraFilaDatos(), hoja.columna("PO")));
        }
    }

    @Test
    void unaCeldaQueNoExisteDevuelveCadenaVacia() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertEquals("", hoja.texto(hoja.primeraFilaDatos(), 4));
            assertEquals("", hoja.texto(hoja.ultimaFila() + 5, 1));
        }
    }

    @Test
    void sinHojaEanAvisaConClaridad() {
        byte[] libroSinEan = PedidoAmiExcel.crear("OTRA COSA",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672));
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> HojaEan.abrir(libroSinEan));
        assertTrue(e.getMessage().contains("EAN"));
    }

    @Test
    void columnaQueNoExisteAvisaConElNombreDeLaHoja() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class, () -> hoja.columna("PRECIO"));
            assertTrue(e.getMessage().contains("PRECIO"));
            assertTrue(e.getMessage().contains("EAN H26"));
        }
    }
}

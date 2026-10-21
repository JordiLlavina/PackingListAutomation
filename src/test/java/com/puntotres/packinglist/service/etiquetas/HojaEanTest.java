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

    /**
     * SS27 llegó con la hoja llamada "Feuil1 Original": sin ninguna "EAN ...",
     * vale la primera que lleva la cabecera de un pedido. La hoja señuelo de
     * delante está vacía y se salta.
     */
    @Test
    void sinHojaEanUsaLaQueTraeLaCabeceraDelPedido() throws Exception {
        byte[] ss27 = PedidoAmiExcel.crearFormatoSs27("Feuil1 Original",
                new Fila(null, "ULL770.AL0245", "001", "NOIR", "U", "09839 PRESS"));
        try (HojaEan hoja = HojaEan.abrir(ss27)) {
            assertEquals("Feuil1 Original", hoja.nombre());
        }
    }

    @Test
    void sinNingunaHojaDePedidoAvisaConClaridad() throws Exception {
        byte[] libro;
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            wb.createSheet("OTRA COSA").createRow(0).createCell(0).setCellValue("PRECIO");
            java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
            wb.write(salida);
            libro = salida.toByteArray();
        }
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> HojaEan.abrir(libro));
        assertTrue(e.getMessage().contains("EAN"), e.getMessage());
        assertTrue(e.getMessage().contains("ARTICLE"), e.getMessage());
    }

    /**
     * Las cabeceras del SS27 se escriben de otra forma ("P.O.", "EAN 13 ")
     * y se comparan sin puntos, espacios ni acentos. Las que cambiaron de
     * nombre ("DESCRIPTION COLORIS", "CODE 128") se piden por sus dos nombres.
     */
    @Test
    void lasCabecerasDelSs27SeEncuentranConLosMismosNombres() throws Exception {
        byte[] ss27 = PedidoAmiExcel.crearFormatoSs27("Feuil1 Original",
                new Fila(null, "ULL770.AL0245", "001", "NOIR", "U", "09839 PRESS"));
        try (HojaEan hoja = HojaEan.abrir(ss27)) {
            assertEquals(0, hoja.columna("ARTICLE"));
            assertEquals(1, hoja.columna("COLORIS"));
            assertEquals(2, hoja.columnaOpcional("LIBELL", "DESCRIPTION COLORIS"));
            assertEquals(4, hoja.columna("TAILLE"));
            assertEquals(5, hoja.columna("PO"));
            assertEquals(6, hoja.columna("COMMAND"));
            assertEquals(7, hoja.columna("EAN13"));
            assertEquals(8, hoja.columnaOpcional("EAN128", "CODE 128"));
            assertEquals(-1, hoja.columnaOpcional("MADE IN"));
        }
    }

    /** El primer nombre que casa manda, aunque el segundo esté más a la izquierda. */
    @Test
    void conVariosNombresGanaElPrimeroQueCasa() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertEquals(9, hoja.columnaOpcional("EAN128", "EAN13"));
        }
    }

    @Test
    void elPaisSaleDelFinalDelEan128() {
        assertEquals("SPAIN",
                HojaEan.madeInDeEan128("366754916126400001000090060000000000000000ES"));
        assertEquals("MOROCCO",
                HojaEan.madeInDeEan128("366754916500000001000098390000000000000000MA"));
        assertEquals("", HojaEan.madeInDeEan128("366754916126400001000090060000000000000000XX"));
        assertEquals("", HojaEan.madeInDeEan128(""));
        assertEquals("", HojaEan.madeInDeEan128(null));
    }

    @Test
    void columnaOpcionalDevuelveElIndiceCuandoLaColumnaEsta() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertEquals(8, hoja.columnaOpcional("EAN13"));
            assertEquals(9, hoja.columnaOpcional("EAN128"));
        }
    }

    @Test
    void columnaOpcionalDevuelveMenosUnoSinLanzar() throws Exception {
        byte[] sinEan = PedidoAmiExcel.crearSinColumnasEan("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672));
        try (HojaEan hoja = HojaEan.abrir(sinEan)) {
            assertEquals(-1, hoja.columnaOpcional("EAN13"));
            assertEquals(-1, hoja.columnaOpcional("EAN128"));
            // Las obligatorias siguen ahí y siguen lanzando si faltan.
            assertEquals(1, hoja.columna("ARTICLE"));
        }
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

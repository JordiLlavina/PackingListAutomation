package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Ancla la resolución de EAN13/EAN128 contra el excel de pedido REAL de AMI
 * ("EAN PUNTOTRES H26.xlsx", copia del de docs/Etiquetas cajas/). Los demás
 * tests usan fixtures sintéticos; este es el que garantiza que la regla de
 * clave exacta funciona con los datos que manda el cliente, rarezas incluidas.
 */
class AmiPedidoRealTest {

    private static AmiPedidoExcel pedido;

    @BeforeAll
    static void cargarElPedidoReal() throws IOException {
        try (InputStream entrada = AmiPedidoRealTest.class
                .getResourceAsStream("/ejemplos/EAN PUNTOTRES H26.xlsx")) {
            assertNotNull(entrada, "Falta src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx");
            pedido = AmiPedidoExcel.desdeBytes(entrada.readAllBytes());
        }
    }

    @Test
    void elFicheroRealTraeTodasLasColumnasQueHacenFalta() {
        assertTrue(pedido.avisos().isEmpty(), pedido.avisos().toString());
    }

    @Test
    void bolso() {
        // Fila 81: ULL027.AL0103 / 001 / U / 07705 CH.
        AmiPedidoExcel.FilaPedido fila =
                pedido.buscar("ULL027.AL0103", "001", null, "CH").orElseThrow();
        assertEquals("07705", fila.orderNumber());
        assertEquals("3666598550098", fila.ean13());
        assertEquals("366659855009800001000077050000000000000000ES", fila.ean128());
        assertTrue(fila.avisosEan().isEmpty(), fila.avisosEan().toString());
    }

    @Test
    void cadaTallaDeCinturonTieneSuEan13() {
        // Filas 28-31: UBL029.AL0216 / 001 / 75-85-95-105 / 7672.
        assertEquals("3666598890040",
                pedido.buscar("UBL029.AL0216", "001", "75", null).orElseThrow().ean13());
        assertEquals("3666598890064",
                pedido.buscar("UBL029.AL0216", "001", "85", null).orElseThrow().ean13());
        assertEquals("3666598890088",
                pedido.buscar("UBL029.AL0216", "001", "95", null).orElseThrow().ean13());
        assertEquals("3666598890101",
                pedido.buscar("UBL029.AL0216", "001", "105", null).orElseThrow().ean13());
    }

    @Test
    void mismoProductoConEan13ComunYEan128PorDestinacion() {
        // Filas 119 y 120: ULL163.AL0052 / 001 / U, en Japan y France.
        AmiPedidoExcel.FilaPedido japan =
                pedido.buscar("ULL163.AL0052", "001", null, "JP").orElseThrow();
        AmiPedidoExcel.FilaPedido france =
                pedido.buscar("ULL163.AL0052", "001", null, null).orElseThrow();

        assertEquals("3666598313495", japan.ean13());
        assertEquals("3666598313495", france.ean13());
        assertTrue(japan.ean128().contains("00007688"), japan.ean128());
        assertTrue(france.ean128().contains("00007663"), france.ean128());
    }

    @Test
    void elColorFormaParteDeLaClave() {
        // Fila 123: la misma referencia en 221 DARK COFFEE tiene otro EAN13.
        assertEquals("3666598354771",
                pedido.buscar("ULL163.AL0052", "221", null, null).orElseThrow().ean13());
    }

    @Test
    void tallaQueNoExisteEnEsaDestinacionNoDaCodigos() {
        // UBL029.AL0104 / 0014 con 07690 JP solo llega hasta la talla 95.
        AmiPedidoExcel.FilaPedido fila =
                pedido.buscar("UBL029.AL0104", "0014", "105", "JP").orElseThrow();

        assertNull(fila.ean13());
        assertNull(fila.ean128());
        assertTrue(fila.avisosEan().stream().anyMatch(a -> a.contains("105")));
        // La talla 95 de la misma caja sí existe.
        assertNotNull(pedido.buscar("UBL029.AL0104", "0014", "95", "JP").orElseThrow().ean13());
    }

    @Test
    void lasFilasAnomalasDelClienteSeImprimenPeroAvisan() {
        // Fila 137: USL728.AL0217 / 001 / U / 7685, Made in MOROCCO pero el
        // EAN128 acaba en ES.
        AmiPedidoExcel.FilaPedido paisRaro =
                pedido.buscar("USL728.AL0217", "001", null, null).orElseThrow();
        assertTrue(paisRaro.ean128().endsWith("ES"), paisRaro.ean128());
        assertTrue(paisRaro.avisosEan().stream().anyMatch(a -> a.contains("EAN128")));

        // Fila 83: ULL027.AL0103 / 718 con 07691 JP lleva dentro el PO 07705.
        AmiPedidoExcel.FilaPedido poIntercambiado =
                pedido.buscar("ULL027.AL0103", "718", null, "JP").orElseThrow();
        assertEquals("07691", poIntercambiado.orderNumber());
        assertTrue(poIntercambiado.ean128().contains("00007705"), poIntercambiado.ean128());
        assertTrue(poIntercambiado.avisosEan().stream().anyMatch(a -> a.contains("EAN128")));
    }

    @Test
    void elColorCodeEsSoloElCodigoYElCompletoLlevaTambienElNombre() {
        // Fila 81: ULL027.AL0103 / 001 (con libellé).
        AmiPedidoExcel.FilaPedido fila =
                pedido.buscar("ULL027.AL0103", "001", null, "CH").orElseThrow();
        assertEquals("001", fila.colorCode());
        assertEquals("001 BLACK", fila.colorCompleto());
    }
}

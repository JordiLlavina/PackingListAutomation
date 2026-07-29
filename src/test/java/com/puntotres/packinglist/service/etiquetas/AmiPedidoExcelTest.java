package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

class AmiPedidoExcelTest {

    /** EAN128 bien formado: EAN13 + 00001 + PO a 8 dígitos + 16 ceros + país. */
    private static String ean128(String ean13, int po, String pais) {
        return ean13 + "00001" + String.format("%08d", po) + "0000000000000000" + pais;
    }

    private static byte[] pedidoTipico() {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07703 CH",
                        "3666598354771", ean128("3666598354771", 7703, "ES")),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07697 JP",
                        "3666598354771", ean128("3666598354771", 7697, "ES")),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665,
                        "3666598354771", ean128("3666598354771", 7665, "ES")),
                // Misma referencia, OTRO color: EAN13 distinto y mismo PO France.
                new Fila("SPAIN", "ULL163.AL0052", "001", "DARK COFFEE", "U", 7665,
                        "3666598313495", ean128("3666598313495", 7665, "ES")),
                // Cinturón: una fila por talla, mismo PO France, EAN13 por talla.
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "85", 7672,
                        "3666598890064", ean128("3666598890064", 7672, "MA")),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "95", 7672,
                        "3666598890088", ean128("3666598890088", 7672, "MA")));
    }

    // --- lo que ya funcionaba y no debe cambiar ---

    @Test
    void encuentraElPoDeCadaDestinacionPorSufijo() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());

        assertEquals("07703",
                pedido.buscar("ULL163.AL0052", "221", null, "CH").orElseThrow().orderNumber());
        assertEquals("07697",
                pedido.buscar("ULL163.AL0052", "221", null, "JP").orElseThrow().orderNumber());
        // PO numérico sin sufijo = France, con padding a 5 dígitos.
        assertEquals("07665",
                pedido.buscar("ULL163.AL0052", "221", null, null).orElseThrow().orderNumber());
    }

    @Test
    void elColorCodeEsColorisMasLibelle() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        assertEquals("001 BLACK",
                pedido.buscar("UBL029.AL0216", "001", "85", null).orElseThrow().colorCode());
    }

    @Test
    void referenciaNoEncontradaDevuelveVacio() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        assertTrue(pedido.buscar("ULL999.XX9999", "001", null, "CH").isEmpty());
        // Referencia existe pero no para esa destinación.
        assertTrue(pedido.buscar("UBL029.AL0216", "001", "85", "CH").isEmpty());
    }

    @Test
    void sinHojaEanFallaConMensajeClaro() {
        byte[] sinEan = PedidoAmiExcel.crear("OTRA COSA");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AmiPedidoExcel.desdeBytes(sinEan));
        assertTrue(error.getMessage().contains("EAN"));
    }

    @Test
    void laHojaEanSeLocalizaPorNombreAunqueNoSeaLaPrimera() throws IOException {
        // pedidoTipico ya mete "BOLSITAS ANTIHUMEDAD" delante; si esto
        // resuelve, es que no se ha asumido "primera hoja del libro".
        Optional<AmiPedidoExcel.FilaPedido> fila =
                AmiPedidoExcel.desdeBytes(pedidoTipico()).buscar("ULL163.AL0052", "221", null, "CH");
        assertTrue(fila.isPresent());
    }

    // --- EAN13 y EAN128 ---

    @Test
    void devuelveLosDosCodigosDeUnBolso() throws IOException {
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedidoTipico())
                .buscar("ULL163.AL0052", "221", null, "CH").orElseThrow();

        assertEquals("3666598354771", fila.ean13());
        assertEquals(ean128("3666598354771", 7703, "ES"), fila.ean128());
        assertTrue(fila.avisosEan().isEmpty());
    }

    @Test
    void elEan13EsComunPeroElEan128CambiaPorDestinacion() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        AmiPedidoExcel.FilaPedido china =
                pedido.buscar("ULL163.AL0052", "221", null, "CH").orElseThrow();
        AmiPedidoExcel.FilaPedido japan =
                pedido.buscar("ULL163.AL0052", "221", null, "JP").orElseThrow();

        assertEquals(china.ean13(), japan.ean13());
        assertEquals(ean128("3666598354771", 7703, "ES"), china.ean128());
        assertEquals(ean128("3666598354771", 7697, "ES"), japan.ean128());
    }

    @Test
    void elColorFormaParteDeLaClave() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        assertEquals("3666598354771",
                pedido.buscar("ULL163.AL0052", "221", null, null).orElseThrow().ean13());
        assertEquals("3666598313495",
                pedido.buscar("ULL163.AL0052", "001", null, null).orElseThrow().ean13());
    }

    @Test
    void cadaTallaDeCinturonTieneSuEan13() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        assertEquals("3666598890064",
                pedido.buscar("UBL029.AL0216", "001", "85", null).orElseThrow().ean13());
        assertEquals("3666598890088",
                pedido.buscar("UBL029.AL0216", "001", "95", null).orElseThrow().ean13());
    }

    @Test
    void tallaQueNoEstaEnElPedidoAvisaYNoDaCodigos() throws IOException {
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedidoTipico())
                .buscar("UBL029.AL0216", "001", "105", null).orElseThrow();

        // El color code y el PO siguen saliendo: lo que falta son los EAN.
        assertEquals("001 BLACK", fila.colorCode());
        assertEquals("07672", fila.orderNumber());
        assertNull(fila.ean13());
        assertNull(fila.ean128());
        assertTrue(fila.avisosEan().stream().anyMatch(a -> a.contains("105")));
    }

    @Test
    void colorQueNoEstaEnElPedidoAvisaYNoDaCodigos() throws IOException {
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedidoTipico())
                .buscar("ULL163.AL0052", "999", null, "CH").orElseThrow();

        // colorCode cae a la primera candidata, como hasta ahora.
        assertEquals("221 BLACK", fila.colorCode());
        assertNull(fila.ean13());
        assertNull(fila.ean128());
        assertTrue(fila.avisosEan().stream().anyMatch(a -> a.contains("999")));
    }

    @Test
    void unEan13InvalidoAvisaYSeDescarta() throws IOException {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665,
                        "3666598354770", ean128("3666598354770", 7665, "ES")));
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedido)
                .buscar("ULL163.AL0052", "221", null, null).orElseThrow();

        // 3666598354770: dígito de control incorrecto (el bueno es 1).
        assertNull(fila.ean13());
        assertTrue(fila.avisosEan().stream().anyMatch(a -> a.contains("3666598354770")));
        // El EAN128 no depende del EAN13 y se sigue dando.
        assertEquals(ean128("3666598354770", 7665, "ES"), fila.ean128());
    }

    @Test
    void unEan128FueraDeEstructuraSeDaIgualPeroAvisa() throws IOException {
        // Caso real del fichero del cliente: Made in MOROCCO pero sufijo ES.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL728.AL0217", "001", "BLACK", "U", 7685,
                        "3666598897124", ean128("3666598897124", 7685, "ES")));
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedido)
                .buscar("USL728.AL0217", "001", null, null).orElseThrow();

        assertEquals(ean128("3666598897124", 7685, "ES"), fila.ean128());
        assertTrue(fila.avisosEan().stream().anyMatch(a -> a.contains("EAN128")));
    }

    @Test
    void unEan128ConElPoDeOtraDestinacionAvisa() throws IOException {
        // Caso real: dos filas con los EAN128 intercambiados entre destinaciones.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL027.AL0103", "718", "BLACK", "U", "07691 JP",
                        "3666598886005", ean128("3666598886005", 7705, "ES")));
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedido)
                .buscar("ULL027.AL0103", "718", null, "JP").orElseThrow();

        assertEquals(ean128("3666598886005", 7705, "ES"), fila.ean128());
        assertTrue(fila.avisosEan().stream().anyMatch(a -> a.contains("EAN128")));
    }

    @Test
    void sinColumnasEanAvisaAlAbrirYLasEtiquetasVanSinCodigos() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(
                PedidoAmiExcel.crearSinColumnasEan("EAN H26",
                        new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665)));

        assertTrue(pedido.avisos().stream().anyMatch(a -> a.contains("EAN13")));
        assertTrue(pedido.avisos().stream().anyMatch(a -> a.contains("EAN128")));
        AmiPedidoExcel.FilaPedido fila =
                pedido.buscar("ULL163.AL0052", "221", null, null).orElseThrow();
        assertEquals("221 BLACK", fila.colorCode());
        assertNull(fila.ean13());
        assertNull(fila.ean128());
    }
}

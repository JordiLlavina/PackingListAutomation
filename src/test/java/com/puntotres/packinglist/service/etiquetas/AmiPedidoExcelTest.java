package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

class AmiPedidoExcelTest {

    private static byte[] pedidoTipico() {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07703 CH"),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07697 JP"),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665),
                // Cinturón: una fila por talla, mismo PO France.
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "85", 7672),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "95", 7672));
    }

    @Test
    void encuentraElPoDeCadaDestinacionPorSufijo() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());

        assertEquals("07703", pedido.buscar("ULL163.AL0052", "221", "CH").orElseThrow().orderNumber());
        assertEquals("07697", pedido.buscar("ULL163.AL0052", "221", "JP").orElseThrow().orderNumber());
        // PO numérico sin sufijo = France, con padding a 5 dígitos.
        assertEquals("07665", pedido.buscar("ULL163.AL0052", "221", null).orElseThrow().orderNumber());
    }

    @Test
    void elColorCodeEsColorisMasLibelle() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        assertEquals("001 BLACK", pedido.buscar("UBL029.AL0216", "001", null).orElseThrow().colorCode());
    }

    @Test
    void referenciaNoEncontradaDevuelveVacio() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        assertTrue(pedido.buscar("ULL999.XX9999", "001", "CH").isEmpty());
        // Referencia existe pero no para esa destinación.
        assertTrue(pedido.buscar("UBL029.AL0216", "001", "CH").isEmpty());
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
                AmiPedidoExcel.desdeBytes(pedidoTipico()).buscar("ULL163.AL0052", "221", "CH");
        assertTrue(fila.isPresent());
    }
}

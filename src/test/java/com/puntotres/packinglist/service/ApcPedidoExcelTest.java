package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Contra el excel de pedido REAL de APC (APC_PEDIDO_FALL26.xlsx, 130 líneas).
 * El libro trae cuatro hojas y dos de ellas (LCT y Sheet2) también tienen
 * "Article" y "Document d'achat": la hoja buena se distingue porque además
 * lleva "Notre référence".
 */
class ApcPedidoExcelTest {

    private static ApcPedidoExcel pedidoReal() throws IOException {
        try (InputStream in = ApcPedidoExcelTest.class
                .getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            return ApcPedidoExcel.desdeBytes(in.readAllBytes());
        }
    }

    @Test
    void encuentraElPedidoCompletoPorReferenciaYTresDigitos() throws IOException {
        assertEquals(Optional.of("4100128721"),
                pedidoReal().pedidoCompleto("PXBHZ-H65077", "721"));
    }

    @Test
    void laMismaReferenciaEnOtroPedidoDaOtroNumero() throws IOException {
        ApcPedidoExcel pedido = pedidoReal();

        assertEquals(Optional.of("4100128707"), pedido.pedidoCompleto("PXBHZ-H65077", "707"));
        assertEquals(Optional.of("4100128720"), pedido.pedidoCompleto("PXBHZ-H65077", "720"));
    }

    @Test
    void unNumeroYaCompletoEncuentraSuPropiaFila() throws IOException {
        assertEquals(Optional.of("4100128721"),
                pedidoReal().pedidoCompleto("PXBHZ-H65077", "4100128721"));
    }

    @Test
    void laReferenciaSeNormalizaEnMayusculasYSinEspacios() throws IOException {
        assertEquals(Optional.of("4100128721"),
                pedidoReal().pedidoCompleto(" pxbhz-h65077 ", "721"));
    }

    @Test
    void referenciaOTresDigitosQueNoExistenNoDevuelvenNada() throws IOException {
        ApcPedidoExcel pedido = pedidoReal();

        assertTrue(pedido.pedidoCompleto("NO-EXISTE", "721").isEmpty());
        assertTrue(pedido.pedidoCompleto("PXBHZ-H65077", "999").isEmpty());
        assertTrue(pedido.pedidoCompleto(null, "721").isEmpty());
        assertTrue(pedido.pedidoCompleto("PXBHZ-H65077", null).isEmpty());
    }

    @Test
    void elFicheroRealNoTieneNingunaClaveAmbigua() throws IOException {
        assertTrue(pedidoReal().avisos().isEmpty(),
                "referencia + 3 dígitos identifica una sola fila en el pedido real");
    }
}

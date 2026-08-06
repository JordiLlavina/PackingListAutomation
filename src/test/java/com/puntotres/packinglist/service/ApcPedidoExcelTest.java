package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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
    void laReferenciaManuscritaCasaPorSufijoYDaLaFilaEntera() throws IOException {
        // En las hojas el operario escribe "67043" o "F63023", nunca el
        // Article completo: la fila se encuentra igual, y trae la referencia
        // entera para estampar en el packing list.
        ApcPedidoExcel pedido = pedidoReal();

        assertEquals(List.of(new ApcPedidoExcel.FilaPedido("PXCEI-F67043", "4100128688")),
                pedido.filasPara("67043", "688"));
        assertEquals(List.of(new ApcPedidoExcel.FilaPedido("PXCBC-F63024", "4100128682")),
                pedido.filasPara("F63024", "682"));
        // Sin la letra también, y con los 3 dígitos desambiguando el modelo
        // que está en varios pedidos.
        assertEquals(List.of(new ApcPedidoExcel.FilaPedido("PXCBT-F65101", "4100128715")),
                pedido.filasPara("65101", "715"));
        assertEquals(List.of(new ApcPedidoExcel.FilaPedido("PXCBT-F65101", "4100128702")),
                pedido.filasPara("F65101", "702"));
    }

    @Test
    void enElFicheroRealTodasLasReferenciasDeLasHojasManuscritasSonUnivocas() throws IOException {
        // Las nueve hojas escaneadas de docs/Packing Lists usan estos códigos:
        // ninguno da más de una fila con su PO. Si una temporada futura
        // rompiera esto, filasPara devolvería varias y el completador avisaría.
        ApcPedidoExcel pedido = pedidoReal();
        for (String[] caso : new String[][] {
                {"H65077", "721"}, {"67043", "688"}, {"63024", "682"},
                {"63023", "704"}, {"F67066", "699"}, {"67080", "685"}}) {
            assertEquals(1, pedido.filasPara(caso[0], caso[1]).size(),
                    caso[0] + "|" + caso[1]);
        }
    }

    @Test
    void unSufijoInventadoNoCasaConNada() throws IOException {
        assertTrue(pedidoReal().filasPara("67008", "721").isEmpty());
    }

    @Test
    void elFicheroRealNoTieneNingunaClaveAmbigua() throws IOException {
        assertTrue(pedidoReal().avisos().isEmpty(),
                "referencia + 3 dígitos identifica una sola fila en el pedido real");
    }
}

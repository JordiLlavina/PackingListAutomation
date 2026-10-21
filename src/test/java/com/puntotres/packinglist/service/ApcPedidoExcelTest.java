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

    /**
     * El nombre del modelo (MODÈLE del packing list) sale de la Désignation,
     * que en el fichero real es una sola por Article.
     */
    @Test
    void elNombreDelModeloSaleDeLaDesignacionDeLaReferencia() throws IOException {
        ApcPedidoExcel pedido = pedidoReal();

        assertEquals("CEINTURE PARIS", pedido.designacionDe("PXBHZ-H65077").orElseThrow());
        assertEquals("LE NEIGE", pedido.designacionDe(" pxcbc-f67008 ").orElseThrow());
        // Recortada, pero sus tres candidatas (PXCDS, PXCEI y PXCBC) dicen lo mismo.
        assertEquals("LE NEIGE CLOU", pedido.designacionDe("F67043").orElseThrow());
        // Recortada y sus candidatas NO coinciden ("pochette neige clou" y
        // "la pochette neige clou"): no se elige ninguna.
        assertTrue(pedido.designacionDe("F63023").isEmpty());
        // Escrita entera, la exacta manda aunque el sufijo sea ambiguo.
        assertEquals("POCHETTE NEIGE CLOU", pedido.designacionDe("PXCBC-F63023").orElseThrow());
        assertTrue(pedido.designacionDe("PXZZZ-F99999").isEmpty());
        assertTrue(pedido.designacionDe(null).isEmpty());
    }

    /**
     * Un pedido en memoria con dos casos que el fichero real no tiene: un
     * artículo cuya Désignation viene vacía y otro que termina igual pero sí
     * tiene nombre.
     */
    private static ApcPedidoExcel pedidoConUnArticuloSinNombre() throws IOException {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook libro =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream()) {
            var hoja = libro.createSheet("Sheet1");
            String[][] filas = {
                    {"Article", "Document d'achat", "Notre référence", "Désignation"},
                    {"PXCBC-F67008", "4100128710", "Wholesale", ""},
                    {"PXCDS-F67008", "4100128711", "Wholesale", "le neige"},
                    {"PXCEI-F67043", "4100128712", "Wholesale", "le neige clou"}};
            for (int f = 0; f < filas.length; f++) {
                var fila = hoja.createRow(f);
                for (int c = 0; c < filas[f].length; c++) {
                    fila.createCell(c).setCellValue(filas[f][c]);
                }
            }
            libro.write(salida);
            return ApcPedidoExcel.desdeBytes(salida.toByteArray());
        }
    }

    /**
     * Primero por la referencia COMPLETA y, solo si ningún Article es esa
     * referencia, por sufijo. Un artículo que está pero no tiene nombre no
     * coge el de otro que termine igual.
     */
    @Test
    void laReferenciaCompletaMandaYElSufijoSoloSiNoSeEncuentra() throws IOException {
        ApcPedidoExcel pedido = pedidoConUnArticuloSinNombre();

        assertEquals("LE NEIGE", pedido.designacionDe("PXCDS-F67008").orElseThrow());
        // Está, pero sin nombre: no coge "le neige" de PXCDS-F67008.
        assertTrue(pedido.designacionDe("PXCBC-F67008").isEmpty());
        // No está entera: por sufijo, con una sola candidata y con nombre.
        assertEquals("LE NEIGE CLOU", pedido.designacionDe("F67043").orElseThrow());
        // Por sufijo, una de las dos candidatas no tiene nombre: no se sabe cuál es.
        assertTrue(pedido.designacionDe("F67008").isEmpty());
    }

    // --- el color: columna Couleurs (Fall 26) o, si no está, el sku (Spring 27) ---

    private static ApcPedidoExcel pedido(String[]... filas) throws IOException {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook libro =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream()) {
            var hoja = libro.createSheet("Sheet1");
            for (int f = 0; f < filas.length; f++) {
                var fila = hoja.createRow(f);
                for (int c = 0; c < filas[f].length; c++) {
                    fila.createCell(c).setCellValue(filas[f][c]);
                }
            }
            libro.write(salida);
            return ApcPedidoExcel.desdeBytes(salida.toByteArray());
        }
    }

    @Test
    void sinColumnaCouleursElColorEsElSkuMenosElArticulo() throws IOException {
        ApcPedidoExcel pedido = pedido(
                new String[] {"Notre référence", "sku", "Document d'achat", "Article", "Valeur de grille"},
                new String[] {"Retail", "PXCBC-F67043LZZ", "4100132821", "PXCBC-F67043", "LZZTU"},
                new String[] {"Retail", "PXCBC-F67043GAA", "4100132821", "PXCBC-F67043", "GAATU"});

        assertEquals(java.util.Set.of("LZZ", "GAA"), pedido.coloresDe("4100132821"));
        assertEquals("LZZ", pedido.catalogo().get(0).color());
        assertEquals("GAA", pedido.lineasConCantidad().get(1).color());
        // El único aviso es el de la cantidad, que este fixture no trae.
        assertTrue(pedido.avisos().stream().noneMatch(aviso -> aviso.contains("color")),
                pedido.avisos().toString());
    }

    /** Con las dos columnas manda Couleurs, que es el color dicho tal cual. */
    @Test
    void conColumnaCouleursMandaElla() throws IOException {
        ApcPedidoExcel pedido = pedido(
                new String[] {"Notre référence", "SKU", "Document d'achat", "Article", "Couleurs"},
                new String[] {"Retail", "PXCBC-F67043LZZ", "4100132821", "PXCBC-F67043", "GAA"});

        assertEquals(java.util.Set.of("GAA"), pedido.coloresDe("4100132821"));
    }

    /**
     * Un sku que no empieza por su Article no se recorta a ojo: esa fila se
     * queda sin color y se dice, porque sin color la entrada por taller no
     * puede comprobar que el taller y el pedido hablan del mismo artículo.
     */
    @Test
    void unSkuQueNoEmpiezaPorSuArticuloNoDaColorYAvisa() throws IOException {
        ApcPedidoExcel pedido = pedido(
                new String[] {"Notre référence", "sku", "Document d'achat", "Article"},
                new String[] {"Retail", "PXCEV-F63037GAA", "4100132845", "PXCBS-F63037"});

        assertTrue(pedido.coloresDe("4100132845").isEmpty());
        assertTrue(pedido.avisos().stream().anyMatch(aviso -> aviso.contains("PXCBS-F63037")),
                pedido.avisos().toString());
    }

    @Test
    void sinCouleursNiSkuAvisaDeQueElPedidoNoTraeColor() throws IOException {
        ApcPedidoExcel pedido = pedido(
                new String[] {"Notre référence", "Document d'achat", "Article"},
                new String[] {"Retail", "4100132821", "PXCBC-F67043"});

        assertTrue(pedido.avisos().stream().anyMatch(aviso -> aviso.contains("color")),
                pedido.avisos().toString());
    }

    /** Contra el pedido real de Spring 27 (162 líneas, sin columna Couleurs). */
    @Test
    void elPedidoRealDeSpring27TraeElColorDeCadaLinea() throws IOException {
        ApcPedidoExcel pedido;
        try (InputStream in = ApcPedidoExcelTest.class
                .getResourceAsStream("/ejemplos/APC_PEDIDO_SPRING27.xlsx")) {
            pedido = ApcPedidoExcel.desdeBytes(in.readAllBytes());
        }

        assertTrue(pedido.avisos().isEmpty(), pedido.avisos().toString());
        assertEquals(162, pedido.catalogo().size());
        assertTrue(pedido.catalogo().stream().noneMatch(linea -> linea.color().isBlank()));
        // Un pedido con cuatro artículos de cuatro colores, en el orden del fichero.
        assertEquals(List.of("FAB", "LZZ", "GAA", "CAV"),
                List.copyOf(pedido.coloresDe("4100132845")));
        assertEquals("4100132821", pedido.pedidoCompleto("F67043", "821").orElseThrow());
        assertEquals("Retail USA", pedido.comandaDe("4100132816").orElseThrow().destino());
    }

    @Test
    void elFicheroRealNoTieneNingunaClaveAmbigua() throws IOException {
        assertTrue(pedidoReal().avisos().isEmpty(),
                "referencia + 3 dígitos identifica una sola fila en el pedido real");
    }
}

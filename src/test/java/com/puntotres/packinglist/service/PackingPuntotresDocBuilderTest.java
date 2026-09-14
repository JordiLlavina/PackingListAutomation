package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.PaletData;

/**
 * El Packing Puntotres se reabre con POI y se leen sus tablas de verdad, como
 * hacen los tests de los builders de Excel.
 */
class PackingPuntotresDocBuilderTest {

    private final PackingPuntotresDocBuilder builder = new PackingPuntotresDocBuilder();

    private static CajaData caja(int numero, String referencia, String color, String talla,
                                 int cantidad, String tamano, Integer palet) {
        CajaData caja = new CajaData(referencia, color, talla, cantidad, null, null);
        caja.setNumeroCaja(numero);
        caja.setTamanoCaja(tamano);
        caja.setNumeroPalet(palet);
        caja.setNumeroPedido("07704");
        return caja;
    }

    private static DatosEnvio cabecera() {
        DatosEnvio cabecera = new DatosEnvio();
        cabecera.setClaveCliente("AMI");
        cabecera.setNumeroFactura("FA-26-1189");
        cabecera.setTemporada("H26");
        cabecera.setFechaEnvio("24/07/2026");
        return cabecera;
    }

    private static PaletData palet(int numero, String medidas) {
        PaletData palet = new PaletData();
        palet.setNumeroPalet(numero);
        palet.setMedidas(medidas);
        return palet;
    }

    private XWPFDocument generar(List<PackingPuntotresDocBuilder.Destino> destinos) throws Exception {
        byte[] bytes = builder.generar(cabecera(), "AMI Paris", destinos);
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }

    /** Las celdas de una fila, ya sin espacios de más. */
    private static List<String> celdas(XWPFTableRow fila) {
        List<String> textos = new ArrayList<>();
        fila.getTableCells().forEach(celda -> textos.add(celda.getText().trim()));
        return textos;
    }

    private static List<List<String>> filas(XWPFTable tabla) {
        List<List<String>> filas = new ArrayList<>();
        tabla.getRows().forEach(fila -> filas.add(celdas(fila)));
        return filas;
    }

    private static String textoDeLosParrafos(XWPFDocument doc) {
        StringBuilder texto = new StringBuilder();
        for (XWPFParagraph parrafo : doc.getParagraphs()) {
            texto.append(parrafo.getText()).append('\n');
        }
        return texto.toString();
    }

    @Test
    void laCabeceraDiceDeQueEnvioEsYElResumenCuentaCadaDestinacion() throws Exception {
        List<CajaData> china = List.of(
                caja(1, "USL728", "221 DARK COFFEE", null, 10, "60x40x40", 1),
                caja(2, "USL728", "221 DARK COFFEE", null, 10, "60x40x40", 1),
                caja(3, "USL728", "221 DARK COFFEE", null, 10, "60x40x40", 2));
        List<CajaData> japan = List.of(
                caja(4, "USL728", "BLACK", null, 7, "60x40x40", 0));

        try (XWPFDocument doc = generar(List.of(
                new PackingPuntotresDocBuilder.Destino("CHINA", china, List.of()),
                new PackingPuntotresDocBuilder.Destino("JAPAN", japan, List.of())))) {
            String parrafos = textoDeLosParrafos(doc);
            // Una sola línea de cabecera, en estilo Título; sin rótulo de
            // documento y sin la factura, que no es un dato del trabajo.
            assertTrue(parrafos.contains("Cliente: AMI Paris   ·   Temporada: H26   ·   Fecha envío: 24/07/2026"));
            assertFalse(parrafos.contains("PACKING PUNTOTRES"));
            assertFalse(parrafos.contains("FA-26-1189"));
            XWPFParagraph cabecera = doc.getParagraphs().get(0);
            assertEquals("Title", cabecera.getStyle());
            assertTrue(cabecera.getRuns().get(0).isBold());

            // La primera tabla es el resumen: destinación, cajas, palets, unidades.
            List<List<String>> resumen = filas(doc.getTables().get(0));
            assertEquals(List.of("Destinación", "Cajas", "Palets", "Unidades"), resumen.get(0));
            assertEquals(List.of("CHINA", "3", "2", "30"), resumen.get(1));
            // Una caja suelta (palet 0) no es un palet.
            assertEquals(List.of("JAPAN", "1", "0", "7"), resumen.get(2));
            assertEquals(List.of("TOTAL ENVÍO", "4", "2", "37"), resumen.get(3));

            // Y una tabla más por destinación, con su título encima.
            assertEquals(3, doc.getTables().size());
            // Nombre, dos tabuladores y el recuento: sin guion ni separador.
            assertTrue(parrafos.contains("CHINA\t\t3 cajas · 2 palets · 30 uds"));
            assertTrue(parrafos.contains("JAPAN\t\t1 cajas · 0 palets · 7 uds"));
        }
    }

    @Test
    void lasCajasVanPorPaletYLasConsecutivasIgualesSeComprimenEnUnaLinea() throws Exception {
        // Desordenadas a propósito: el documento las pone en el orden del trabajo.
        List<CajaData> cajas = List.of(
                caja(6, "USL728", "BLACK", null, 10, "60x40x40", 2),
                caja(1, "USL728", "221 DARK COFFEE", null, 10, "60x40x40", 1),
                caja(2, "USL728", "221 DARK COFFEE", null, 10, "60x40x40", 1),
                caja(3, "USL728", "221 DARK COFFEE", null, 10, "60x40x40", 1),
                caja(4, "USL728", "221 DARK COFFEE", null, 4, "60x40x40", 1),
                caja(5, "USL728", "BLACK", null, 10, "60x40x40", 2));

        try (XWPFDocument doc = generar(List.of(new PackingPuntotresDocBuilder.Destino(
                "CHINA", cajas, List.of(palet(1, "80x120x130")))))) {
            List<List<String>> tabla = filas(doc.getTables().get(1));

            assertEquals(List.of("Cajas", "Nº", "Referencia", "Color", "Talla",
                    "Uds/caja", "Total", "Caja"), tabla.get(0));
            // Rótulo del palet: rango leído de las cajas, recuento y medidas.
            assertEquals(List.of("PALET 1 · cajas 1-4 · 4 cajas · 34 uds · 80x120x130"), tabla.get(1));
            // Las tres iguales en una línea; la cuarta, con otra cantidad, aparte.
            assertEquals(List.of("1-3", "3", "USL728", "221 DARK COFFEE", "", "10", "30", "60x40x40"),
                    tabla.get(2));
            assertEquals(List.of("4", "1", "USL728", "221 DARK COFFEE", "", "4", "4", "60x40x40"),
                    tabla.get(3));
            // El palet 2 no está declarado: sin medidas, y no pasa nada.
            assertEquals(List.of("PALET 2 · cajas 5-6 · 2 cajas · 20 uds"), tabla.get(4));
            assertEquals(List.of("5-6", "2", "USL728", "BLACK", "", "10", "20", "60x40x40"),
                    tabla.get(5));
            assertEquals(6, tabla.size());
        }
    }

    @Test
    void unBultoMixtoSaleLineaALineaYCuentaUnaSolaCaja() throws Exception {
        List<CajaData> cajas = List.of(
                caja(1, "UBL029", "2221", "75", 6, "60x40x30", 1),
                caja(1, "UBL029", "2221", "80", 6, "60x40x30", 1),
                caja(1, "UBL029", "2221", "85", 4, "60x40x30", 1),
                caja(2, "UBL029", "2221", "75", 12, "60x40x30", 1));

        try (XWPFDocument doc = generar(List.of(
                new PackingPuntotresDocBuilder.Destino("CHINA", cajas, List.of())))) {
            List<List<String>> tabla = filas(doc.getTables().get(1));
            assertEquals(List.of("PALET 1 · cajas 1-2 · 2 cajas · 28 uds"), tabla.get(1));
            assertEquals(List.of("1", "1", "UBL029", "2221", "75", "6", "6", "60x40x30"), tabla.get(2));
            // Las líneas siguientes del mismo bulto no vuelven a contar la caja.
            assertEquals(List.of("1", "", "UBL029", "2221", "80", "6", "6", "60x40x30"), tabla.get(3));
            assertEquals(List.of("1", "", "UBL029", "2221", "85", "4", "4", "60x40x30"), tabla.get(4));
            assertEquals(List.of("2", "1", "UBL029", "2221", "75", "12", "12", "60x40x30"), tabla.get(5));
        }
    }

    @Test
    void lasSueltasYLasSinPaletLlevanSuPropioRotuloDetrasDeLosPalets() throws Exception {
        List<CajaData> cajas = List.of(
                caja(1, "USL728", "BLACK", null, 10, "60x40x40", null),
                caja(2, "USL728", "BLACK", null, 10, "60x40x40", 0),
                caja(3, "USL728", "BLACK", null, 10, "60x40x40", 1));

        try (XWPFDocument doc = generar(List.of(
                new PackingPuntotresDocBuilder.Destino("CHINA", cajas, List.of())))) {
            List<List<String>> tabla = filas(doc.getTables().get(1));
            assertEquals("PALET 1 · caja 3 · 1 caja · 10 uds", tabla.get(1).get(0));
            assertEquals("SIN PALET (cajas sueltas) · caja 2 · 1 caja · 10 uds", tabla.get(3).get(0));
            assertEquals("SIN PALET ASIGNADO · caja 1 · 1 caja · 10 uds", tabla.get(5).get(0));
        }
    }

    /**
     * Deja {@code target/Packing Puntotres ejemplo.docx} para abrirlo en Word
     * y revisar la maquetación a ojo, como hacen los tests de flujo con los
     * excels. Lo único que se comprueba aquí es que el fichero existe y se
     * vuelve a abrir.
     */
    @Test
    void dejaUnEjemploEnTargetParaRevisarloEnWord() throws Exception {
        List<CajaData> china = new ArrayList<>();
        for (int numero = 1; numero <= 12; numero++) {
            china.add(caja(numero, "USL728.AL217", "221 DARK COFFEE", null, 10, "60x40x40", 1));
        }
        for (int numero = 13; numero <= 20; numero++) {
            china.add(caja(numero, "USL728.AL217", "NOIR", null, 10, "60x40x40", 2));
        }
        china.add(caja(21, "USL728.AL217", "NOIR", null, 3, "60x40x40", 2));
        china.add(caja(22, "UBL029.AL0216", "2221", "75", 6, "60x40x30", 3));
        china.add(caja(22, "UBL029.AL0216", "2221", "80", 6, "60x40x30", 3));
        china.add(caja(22, "UBL029.AL0216", "2221", "85", 4, "60x40x30", 3));
        china.add(caja(23, "UBL029.AL0216", "2221", "90", 12, "60x40x30", 3));
        List<CajaData> japan = List.of(
                caja(24, "USL728.AL217", "NOIR", null, 10, "60x40x40", 0),
                caja(25, "USL728.AL217", "NOIR", null, 5, "60x40x40", 0));

        byte[] bytes = builder.generar(cabecera(), "AMI Paris", List.of(
                new PackingPuntotresDocBuilder.Destino("CHINA", china,
                        List.of(palet(1, "80x120x130"), palet(2, "80x120x130"), palet(3, "80x120x90"))),
                new PackingPuntotresDocBuilder.Destino("JAPAN", japan, List.of())));
        java.nio.file.Path fichero = java.nio.file.Path.of("target", "Packing Puntotres ejemplo.docx");
        java.nio.file.Files.createDirectories(fichero.getParent());
        try {
            java.nio.file.Files.write(fichero, bytes);
        } catch (java.nio.file.FileSystemException abierto) {
            // Word bloquea el fichero mientras lo tiene abierto, que es
            // justo lo que pasa mientras se revisa la maquetación: se deja
            // al lado con otro nombre en vez de fallar o de no dejar nada.
            fichero = fichero.resolveSibling("Packing Puntotres ejemplo (nuevo).docx");
            java.nio.file.Files.write(fichero, bytes);
        }

        try (XWPFDocument doc = new XWPFDocument(java.nio.file.Files.newInputStream(fichero))) {
            assertEquals(3, doc.getTables().size());
        }
    }

    @Test
    void unaLineaNuncaTapaUnaDiferenciaEntreSusCajas() {
        // Mismo artículo y palet pero distinto cartón: dos líneas. Y un hueco
        // en la numeración también parte el tramo.
        List<CajaData> cajas = List.of(
                caja(1, "USL728", "BLACK", null, 10, "60x40x40", 1),
                caja(2, "USL728", "BLACK", null, 10, "60x40x30", 1),
                caja(3, "USL728", "BLACK", null, 10, "60x40x30", 1),
                caja(5, "USL728", "BLACK", null, 10, "60x40x30", 1));

        var porPalet = PackingPuntotresDocBuilder.lineasPorPalet(cajas);
        assertEquals(3, porPalet.get(1).size());
        assertFalse(porPalet.containsKey(null));
    }
}

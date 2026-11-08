package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;

/**
 * El Word de etiquetas de SMS, reabierto con POI. Deja además
 * {@code target/Etiquetas SMS ejemplo.docx} para abrirlo en Word y comparar
 * con la plantilla del usuario ("docs/Etiquetas Artículos/ETIQUETAS PEQUEÑAS
 * Template.doc").
 */
class EtiquetasSmsDocBuilderTest {

    private static final EtiquetaSms NEIGE =
            new EtiquetaSms("Fall 27", "PXCBC-F67008", "LE NEIGE", "ALPACA TOUCH", "VERT FONCE");
    private static final EtiquetaSms DOS_PIELES = new EtiquetaSms("FALL 27", "PXBHZ-F65101", "LA POCHETTE",
            "BOMBARDIER BLK FIX ENC 12MM CM CAMEL + NOBILE", "ECRU/CAE MARRON FONCÉ");

    private static XWPFDocument abrir(byte[] word) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(word));
    }

    private static List<String> textos(XWPFTableCell celda) {
        return celda.getParagraphs().stream().map(XWPFParagraph::getText).toList();
    }

    private static double tamano(XWPFParagraph parrafo) {
        XWPFRun run = parrafo.getRuns().get(0);
        return run.getFontSizeAsDouble();
    }

    @Test
    void cadaEtiquetaSonSusSieteLineasEnMayusculas() throws IOException {
        try (XWPFDocument doc = abrir(new EtiquetasSmsDocBuilder().generar(List.of(NEIGE)))) {
            XWPFTableCell celda = doc.getTables().get(0).getRow(0).getCell(0);
            assertEquals(List.of("PUNTOTRES", "FALL 27", "SMS", "PXCBC-F67008", "LE NEIGE", "ALPACA TOUCH",
                    "VERT FONCE"), textos(celda));
            for (XWPFParagraph parrafo : celda.getParagraphs()) {
                assertEquals("Calibri", parrafo.getRuns().get(0).getFontFamily());
                assertEquals(EtiquetasSmsDocBuilder.TAMANO, tamano(parrafo));
            }
        }
    }

    @Test
    void laRejillaEsLaDeLaPlantillaCincoPorTreceConMedidasExactas() throws IOException {
        List<EtiquetaSms> etiquetas = new ArrayList<>();
        for (int i = 0; i < 67; i++) {
            etiquetas.add(NEIGE);
        }
        try (XWPFDocument doc = abrir(new EtiquetasSmsDocBuilder().generar(etiquetas))) {
            XWPFTable tabla = doc.getTables().get(0);
            // 67 etiquetas son 13 filas y media: la última fila lleva dos y tres casillas vacías.
            assertEquals(14, tabla.getNumberOfRows());
            for (XWPFTableRow fila : tabla.getRows()) {
                assertEquals(EtiquetasSmsDocBuilder.ALTO_CELDA, fila.getHeight());
                assertEquals(TableRowHeightRule.EXACT, fila.getHeightRule());
                assertTrue(fila.isCantSplitRow());
                assertEquals(5, fila.getTableCells().size());
                for (XWPFTableCell celda : fila.getTableCells()) {
                    assertEquals(EtiquetasSmsDocBuilder.ANCHO_CELDA, celda.getWidth());
                }
            }
            XWPFTableRow ultima = tabla.getRow(13);
            assertEquals("PUNTOTRES", ultima.getCell(1).getParagraphs().get(0).getText());
            assertEquals("", ultima.getCell(2).getText());

            CTPageMar margen = doc.getDocument().getBody().getSectPr().getPgMar();
            // El texto de la primera columna, a 660 twips del borde como en la plantilla: la
            // casilla empieza su margen (96) antes.
            int izquierdo = ((Number) margen.getLeft()).intValue();
            assertEquals(660, izquierdo + EtiquetasSmsDocBuilder.MARGEN_CELDA);
            assertEquals(567, ((Number) margen.getTop()).intValue());
            // Las cinco columnas llenan justo el ancho entre márgenes.
            assertEquals(EtiquetasSmsDocBuilder.ANCHO_PAGINA, izquierdo + ((Number) margen.getRight()).intValue()
                    + 5 * EtiquetasSmsDocBuilder.ANCHO_CELDA);
            // Trece filas caben en el alto útil del A4: si no, la decimotercera saltaría de hoja.
            assertTrue(EtiquetasSmsDocBuilder.FILAS_POR_PAGINA * EtiquetasSmsDocBuilder.ALTO_CELDA
                    <= EtiquetasSmsDocBuilder.ALTO_PAGINA - 2 * 567);
        }
    }

    @Test
    void lasSieteLineasCabenEnElAltoDeLaCasilla() {
        assertTrue(7 * EtiquetasSmsDocBuilder.ALTO_LINEA <= EtiquetasSmsDocBuilder.ALTO_CELDA);
    }

    @Test
    void unaLineaLargaBajaDeLetraHastaQueCabeYLasCortasNo() throws IOException {
        try (XWPFDocument doc = abrir(new EtiquetasSmsDocBuilder().generar(List.of(DOS_PIELES)))) {
            List<XWPFParagraph> lineas = doc.getTables().get(0).getRow(0).getCell(0).getParagraphs();
            assertEquals("BOMBARDIER BLK FIX ENC 12MM CM CAMEL + NOBILE", lineas.get(5).getText());
            double piel = tamano(lineas.get(5));
            assertTrue(piel < EtiquetasSmsDocBuilder.TAMANO && piel >= EtiquetasSmsDocBuilder.TAMANO_MINIMO,
                    "tamaño " + piel);
            assertEquals(EtiquetasSmsDocBuilder.TAMANO, tamano(lineas.get(3)));
            assertEquals("ECRU/CAE MARRON FONCÉ", lineas.get(6).getText());
        }
        assertTrue(EtiquetasSmsDocBuilder.cabe("BOMBARDIER BLK FIX ENC 12MM CM CAMEL + NOBILE"));
        assertFalse(EtiquetasSmsDocBuilder.cabe("BOMBARDIER BLK FIX ENC 12MM CM CAMEL + NOBILE + OTRA PIEL MAS"));
    }

    @Test
    void dejaUnEjemploParaAbrirloEnWord() throws IOException {
        List<EtiquetaSms> etiquetas = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            etiquetas.add(i % 7 == 3 ? DOS_PIELES : NEIGE);
        }
        Path destino = Path.of("target", "Etiquetas SMS ejemplo.docx");
        Files.createDirectories(destino.getParent());
        Files.write(destino, new EtiquetasSmsDocBuilder().generar(etiquetas));
        assertTrue(Files.size(destino) > 0);
    }
}

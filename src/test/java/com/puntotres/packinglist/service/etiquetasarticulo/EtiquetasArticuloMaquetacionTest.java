package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Compara la maquetación generada contra la del fichero REAL del cliente.
 *
 * Sin plantilla .xlsx, las medidas viven como constantes en el builder: este
 * test es lo que impide que deriven. Si alguien cambia un ancho, un alto o
 * el pageSetup, cae aquí señalando el fichero del cliente como verdad.
 */
class EtiquetasArticuloMaquetacionTest {

    private static final Path FICHERO_REAL =
            Path.of("docs/Etiquetas para etiquetas/AMI CODE BARRE H26 MOROCCO.xlsx");

    @Test
    void laMaquetacionCoincideConElFicheroRealDelCliente() throws Exception {
        assertTrue(Files.exists(FICHERO_REAL),
                "falta el fichero de referencia del cliente: " + FICHERO_REAL);

        byte[] generado = new EtiquetasArticuloExcelBuilder().generar(List.of(
                new HojaEtiquetas("HOJA", new EtiquetaArticulo(
                        "USL738.AL0137", "Size: U", "A236 TRUFFLE", "Cde: 07714",
                        "3666598543892"))));

        try (InputStream real = Files.newInputStream(FICHERO_REAL);
             XSSFWorkbook libroReal = new XSSFWorkbook(real);
             XSSFWorkbook libroGenerado = new XSSFWorkbook(new ByteArrayInputStream(generado))) {

            XSSFSheet esperada = libroReal.getSheetAt(0);
            XSSFSheet obtenida = libroGenerado.getSheetAt(0);

            for (int columna = 0; columna < 11; columna++) {
                assertEquals(esperada.getColumnWidth(columna), obtenida.getColumnWidth(columna),
                        "ancho de la columna " + columna);
            }

            assertEquals(esperada.getRow(0).getHeightInPoints(),
                    obtenida.getRow(0).getHeightInPoints(), 0.001f, "alto de la fila 0");
            assertEquals(esperada.getDefaultRowHeightInPoints(),
                    obtenida.getDefaultRowHeightInPoints(), 0.001f, "alto por defecto");

            // Filas separadoras: la del último bloque (índice 80) no existe en
            // el fichero real, que no tiene celdas más allá de la fila 74.
            for (int bloque = 0; bloque < EtiquetasArticuloExcelBuilder.BLOQUES - 1; bloque++) {
                int separadora = EtiquetasArticuloExcelBuilder.filaBase(bloque)
                        + EtiquetasArticuloExcelBuilder.FILAS_POR_BLOQUE - 1;
                assertEquals(esperada.getRow(separadora).getHeightInPoints(),
                        obtenida.getRow(separadora).getHeightInPoints(), 0.001f,
                        "alto de la fila separadora " + separadora);
            }

            assertEquals(esperada.getPrintSetup().getScale(),
                    obtenida.getPrintSetup().getScale(), "escala de impresión");
            assertEquals(esperada.getPrintSetup().getPaperSize(),
                    obtenida.getPrintSetup().getPaperSize(), "tamaño de papel");
            assertEquals(esperada.getPrintSetup().getLandscape(),
                    obtenida.getPrintSetup().getLandscape(), "orientación");

            for (PageMargin margen : new PageMargin[] {
                    PageMargin.LEFT, PageMargin.RIGHT, PageMargin.TOP, PageMargin.BOTTOM}) {
                assertEquals(esperada.getMargin(margen), obtenida.getMargin(margen), 1e-9,
                        "margen " + margen);
            }
        }
    }

    @Test
    void losValoresEsperadosSonLosDelSpike() throws Exception {
        // Duplicado deliberado: si el fichero de docs/ desaparece o cambia,
        // estos valores siguen documentando qué maquetación se espera.
        byte[] generado = new EtiquetasArticuloExcelBuilder().generar(List.of(
                new HojaEtiquetas("HOJA", new EtiquetaArticulo(
                        "REF", "Size: U", "001 BLACK", "Cde: 1", null))));

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(generado))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            int[] anchos = {3766, 4425, 621, 3766, 4534, 512, 3766, 4534, 621, 3766, 4534};
            for (int columna = 0; columna < anchos.length; columna++) {
                assertEquals(anchos[columna], hoja.getColumnWidth(columna),
                        "ancho de la columna " + columna);
            }
            assertEquals(6.0f, hoja.getRow(0).getHeightInPoints(), 0.001f);
            assertEquals(9.95f, hoja.getRow(8).getHeightInPoints(), 0.001f);
            assertEquals(15.0f, hoja.getDefaultRowHeightInPoints(), 0.001f);
            assertEquals(74, hoja.getPrintSetup().getScale());
            assertEquals(9, hoja.getPrintSetup().getPaperSize());
            assertEquals(false, hoja.getPrintSetup().getLandscape());
            assertEquals(0.0, hoja.getMargin(PageMargin.LEFT), 1e-9);
            assertEquals(0.03937007874015748, hoja.getMargin(PageMargin.RIGHT), 1e-9);
            assertEquals(0.03937007874015748, hoja.getMargin(PageMargin.TOP), 1e-9);
            assertEquals(0.03937007874015748, hoja.getMargin(PageMargin.BOTTOM), 1e-9);
        }
    }
}

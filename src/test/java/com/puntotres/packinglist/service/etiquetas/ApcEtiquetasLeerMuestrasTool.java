package com.puntotres.packinglist.service.etiquetas;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Herramienta manual: lee los excels de muestra AJUSTADOS A MANO en
 * target/muestras-etiquetas-apc/ y vuelca todo lo que hay que trasladar al
 * código (altos de fila, anchos de columna, ajuste de página, márgenes y
 * área de impresión), para no transcribir números a ojo.
 *
 * <p>Ejecutar: mvn test -Dtest=ApcEtiquetasLeerMuestrasTool
 */
class ApcEtiquetasLeerMuestrasTool {

    private static final Path DIR = Path.of("target", "muestras-etiquetas-apc");

    @Test
    void volcarLoAjustado() throws IOException {
        for (String nombre : new String[] {"JAPAN", "KOREA", "D. USA", "WHOLESALE", "RETAIL"}) {
            Path fichero = DIR.resolve("Etiquetas APC " + nombre + ".xlsx");
            System.out.println("=== " + nombre + "   " + fichero);
            try (FileInputStream in = new FileInputStream(fichero.toFile());
                 XSSFWorkbook libro = new XSSFWorkbook(in)) {
                for (int i = 0; i < libro.getNumberOfSheets(); i++) {
                    volcarHoja(libro, i);
                }
            }
        }
    }

    private static void volcarHoja(XSSFWorkbook libro, int indice) {
        XSSFSheet hoja = libro.getSheetAt(indice);
        System.out.println("  --- hoja[" + indice + "] '" + libro.getSheetName(indice)
                + "'  ultimaFila(0based)=" + hoja.getLastRowNum());
        System.out.println("    areaImpresion: " + libro.getPrintArea(indice));
        System.out.println("    pageSetup: paper=" + hoja.getPrintSetup().getPaperSize()
                + " landscape=" + hoja.getPrintSetup().getLandscape()
                + " scale=" + hoja.getPrintSetup().getScale()
                + " fitToPage=" + hoja.getFitToPage()
                + " fitWidth=" + hoja.getPrintSetup().getFitWidth()
                + " fitHeight=" + hoja.getPrintSetup().getFitHeight()
                + " autobreaks=" + hoja.getAutobreaks());
        System.out.println("    margenes(pulg): sup=" + hoja.getMargin(Sheet.TopMargin)
                + " inf=" + hoja.getMargin(Sheet.BottomMargin)
                + " izq=" + hoja.getMargin(Sheet.LeftMargin)
                + " der=" + hoja.getMargin(Sheet.RightMargin)
                + " header=" + hoja.getMargin(Sheet.HeaderMargin)
                + " footer=" + hoja.getMargin(Sheet.FooterMargin));
        StringBuilder anchos = new StringBuilder();
        for (int c = 0; c < 8; c++) {
            anchos.append((char) ('A' + c)).append('=')
                  .append(Math.round(hoja.getColumnWidth(c) / 25.6) / 10.0).append("  ");
        }
        System.out.println("    anchos: " + anchos);
        StringBuilder altos = new StringBuilder();
        for (int f = 0; f <= hoja.getLastRowNum(); f++) {
            float alto = hoja.getRow(f) != null
                    ? hoja.getRow(f).getHeightInPoints() : hoja.getDefaultRowHeightInPoints();
            altos.append(f + 1).append('=').append(alto).append("  ");
        }
        System.out.println("    altos: " + altos);
        System.out.println("    saltosDeFila: " + java.util.Arrays.toString(hoja.getRowBreaks()));
    }

    /**
     * Dónde han quedado los VALORES que escribió el builder en cada muestra
     * ajustada: es lo que dice si las coordenadas del layout siguen valiendo.
     * Busca los valores de la muestra por su texto y da su fila 1-based.
     */
    @Test
    void volcarDondeQuedaronLosValores() throws IOException {
        for (String nombre : new String[] {"JAPAN", "KOREA", "D. USA", "WHOLESALE", "RETAIL"}) {
            Path fichero = DIR.resolve("Etiquetas APC " + nombre + ".xlsx");
            System.out.println("=== " + nombre);
            try (FileInputStream in = new FileInputStream(fichero.toFile());
                 XSSFWorkbook libro = new XSSFWorkbook(in)) {
                for (int i = 0; i < libro.getNumberOfSheets(); i++) {
                    XSSFSheet hoja = libro.getSheetAt(i);
                    System.out.println("  --- hoja[" + i + "] '" + libro.getSheetName(i) + "'");
                    for (int f = 0; f <= hoja.getLastRowNum(); f++) {
                        if (hoja.getRow(f) == null) {
                            continue;
                        }
                        StringBuilder linea = new StringBuilder();
                        for (int c = 0; c < 6; c++) {
                            org.apache.poi.ss.usermodel.Cell celda = hoja.getRow(f).getCell(c);
                            String v = celda == null ? "" : switch (celda.getCellType()) {
                                case STRING -> celda.getStringCellValue();
                                case NUMERIC -> String.valueOf(celda.getNumericCellValue());
                                default -> "";
                            };
                            if (v != null && !v.isBlank()) {
                                linea.append(" [").append((char) ('A' + c)).append("] ")
                                     .append(v.replace('\n', '~'));
                            }
                        }
                        if (linea.length() > 0) {
                            System.out.println("    f" + (f + 1) + linea);
                        }
                    }
                    System.out.println("    merges: " + hoja.getMergedRegions());
                    System.out.println("    imagenes: " + (hoja.getDrawingPatriarch() == null ? 0
                            : hoja.getDrawingPatriarch().getShapes().size()));
                }
            }
        }
    }

}

package com.puntotres.packinglist.service.etiquetas;

import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * El número del palet ("Nº3") en la esquina superior derecha de su etiqueta
 * de palet, a 11 puntos. Lo comparten los clientes con etiquetas de palet
 * (AMI y APC): el formato del número es una sola decisión.
 *
 * Sirve para saber qué etiqueta va en qué palet sin tener que contar cajas.
 * Sustituye al contador que el cliente apuntaba a mano en la primera fila
 * de la plantilla, que el builder blanquea y no replica.
 *
 * El estilo se crea UNA vez por estilo de origen de la celda, no por
 * etiqueta: un envío con muchos palets no debe llenar el libro de estilos
 * repetidos. El de la plantilla no se toca en sitio porque lo comparten
 * otras celdas de la hoja.
 */
final class NumeroPaletEtiqueta {

    static final short TAMANO_FUENTE = 11;

    private final XSSFWorkbook libro;
    private final Map<Integer, XSSFCellStyle> estilos = new HashMap<>();

    NumeroPaletEtiqueta(XSSFWorkbook libro) {
        this.libro = libro;
    }

    static String texto(int numeroPalet) {
        return "Nº" + numeroPalet;
    }

    void escribir(XSSFSheet hoja, int fila, int columna, int numeroPalet) {
        XSSFRow f = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell celda = f.getCell(columna) != null ? f.getCell(columna) : f.createCell(columna);
        XSSFCellStyle original = (XSSFCellStyle) celda.getCellStyle();
        celda.setCellStyle(estilos.computeIfAbsent((int) original.getIndex(), indice -> {
            XSSFCellStyle estilo = libro.createCellStyle();
            estilo.cloneStyleFrom(original);
            XSSFFont fuente = libro.createFont();
            fuente.setFontName(original.getFont().getFontName());
            fuente.setFontHeightInPoints(TAMANO_FUENTE);
            estilo.setFont(fuente);
            // Pegado a la esquina: a la derecha y arriba, aunque la fila sea
            // alta (en AMI es la del remitente, de casi 90 puntos).
            estilo.setAlignment(HorizontalAlignment.RIGHT);
            estilo.setVerticalAlignment(VerticalAlignment.TOP);
            estilo.setWrapText(false);
            return estilo;
        }));
        celda.setCellValue(texto(numeroPalet));
    }
}

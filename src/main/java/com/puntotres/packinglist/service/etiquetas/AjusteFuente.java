package com.puntotres.packinglist.service.etiquetas;

import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Encoge la fuente de una celda cuando su texto no cabe en el ancho de su
 * columna, sin tocar el tamaño de la celda ni el de la etiqueta.
 *
 * Hace falta porque una etiqueta de caja con varios artículos concatena sus
 * campos con " / " y el texto crece: en la plantilla de AMI la columna C da
 * para unos 28 caracteres a la fuente de REFERENCE, y dos artículos ya llegan
 * justos.
 *
 * Se hacen las DOS cosas a la vez:
 * <ul>
 * <li>se <b>calcula</b> el tamaño y se escribe en el estilo, porque es
 * determinista y un test puede afirmarlo reabriendo el .xlsx;</li>
 * <li>se marca además <b>shrinkToFit</b>, que es el "Reducir hasta ajustar"
 * de Excel, como red de seguridad para cuando el texto se pasa incluso al
 * tamaño mínimo. Funciona aquí porque las celdas de valor de la plantilla no
 * están combinadas ni tienen wrapText — Excel lo ignora en esos dos casos.</li>
 * </ul>
 *
 * Si el texto cabe, no se toca nada: una caja de un solo artículo produce el
 * mismo fichero que antes de existir esta clase.
 */
public final class AjusteFuente {

    /** Por debajo de esto no se lee en una etiqueta impresa. */
    public static final int TAMANO_MINIMO_PT = 8;

    /**
     * Excel mide el ancho de columna en caracteres de su fuente por defecto,
     * que es de 11 pt: la capacidad a otro tamaño se escala con esto.
     */
    private static final double TAMANO_DE_REFERENCIA_PT = 11.0;

    private final XSSFWorkbook libro;
    private final Map<String, XSSFCellStyle> estilos = new HashMap<>();

    public AjusteFuente(XSSFWorkbook libro) {
        this.libro = libro;
    }

    /**
     * Tamaño de fuente con el que el texto cabe en una columna de ese ancho.
     * anchoEnChars es getColumnWidth()/256: caracteres de la fuente por
     * defecto. Devuelve el tamaño original si ya cabe.
     */
    public static short tamano(String texto, double anchoEnChars, short tamanoOriginalPt) {
        if (texto == null || texto.isBlank() || tamanoOriginalPt <= TAMANO_MINIMO_PT) {
            return tamanoOriginalPt;
        }
        double capacidad = anchoEnChars * TAMANO_DE_REFERENCIA_PT / tamanoOriginalPt;
        if (texto.length() <= capacidad) {
            return tamanoOriginalPt;
        }
        double escalado = tamanoOriginalPt * capacidad / texto.length();
        return (short) Math.max(TAMANO_MINIMO_PT, Math.floor(escalado));
    }

    /** Ajusta la fuente de la celda a su contenido; no hace nada si cabe. */
    public void ajustar(Cell celda) {
        if (celda.getCellType() != CellType.STRING) {
            return;
        }
        XSSFCellStyle original = (XSSFCellStyle) celda.getCellStyle();
        short tamanoOriginal = original.getFont().getFontHeightInPoints();
        double anchoEnChars = celda.getSheet().getColumnWidth(celda.getColumnIndex()) / 256.0;
        short nuevo = tamano(celda.getStringCellValue(), anchoEnChars, tamanoOriginal);
        if (nuevo == tamanoOriginal) {
            return;
        }
        celda.setCellStyle(estiloCon(original, nuevo));
    }

    /** El estilo original con otro tamaño de fuente, creado una sola vez. */
    private XSSFCellStyle estiloCon(XSSFCellStyle original, short tamano) {
        return estilos.computeIfAbsent(original.getIndex() + ":" + tamano, clave -> {
            XSSFFont fuenteOriginal = original.getFont();
            XSSFFont fuente = libro.createFont();
            fuente.setFontName(fuenteOriginal.getFontName());
            fuente.setBold(fuenteOriginal.getBold());
            fuente.setItalic(fuenteOriginal.getItalic());
            if (fuenteOriginal.getXSSFColor() != null) {
                fuente.setColor(fuenteOriginal.getXSSFColor());
            }
            fuente.setFontHeightInPoints(tamano);
            XSSFCellStyle estilo = libro.createCellStyle();
            estilo.cloneStyleFrom(original);
            estilo.setFont(fuente);
            estilo.setShrinkToFit(true);
            return estilo;
        });
    }
}

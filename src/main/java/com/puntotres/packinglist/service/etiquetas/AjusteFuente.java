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
 * Se hacen las DOS cosas a la vez, pero shrinkToFit solo cuando puede
 * funcionar:
 * <ul>
 * <li>se <b>calcula</b> el tamaño y se escribe en el estilo, porque es
 * determinista y un test puede afirmarlo reabriendo el .xlsx. Esto es lo
 * único que protege el texto en las plantillas de <b>APC</b>: sus celdas de
 * valor llevan wrapText, así que el shrinkToFit de abajo no hace nada ahí
 * (ver excepción);</li>
 * <li>se marca además <b>shrinkToFit</b>, que es el "Reducir hasta ajustar"
 * de Excel, como red de seguridad para cuando el texto se pasa incluso al
 * tamaño mínimo — pero <b>solo si el estilo original no tiene wrapText</b>:
 * Excel ignora shrinkToFit en una celda con wrapText (gana el ajuste de
 * línea, y el texto se corta en una fila de altura fija en vez de encoger),
 * así que marcarlo ahí sería un atributo escrito que no hace nada y que
 * podría hacer creer que el mecanismo está activo cuando no lo está. En la
 * plantilla de <b>AMI</b> las celdas de valor no están combinadas ni tienen
 * wrapText, así que shrinkToFit sí es una red de seguridad real ahí.</li>
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
     * defecto. Devuelve el tamaño original si ya cabe, o si el original ya
     * está en el suelo o por debajo (no hay margen para bajarlo más: la red
     * de seguridad para ese caso es shrinkToFit, que aplica ajustar(), no
     * este cálculo).
     */
    public static short tamano(String texto, double anchoEnChars, short tamanoOriginalPt) {
        if (texto == null || texto.isBlank() || tamanoOriginalPt <= TAMANO_MINIMO_PT) {
            return tamanoOriginalPt;
        }
        double capacidad = capacidad(anchoEnChars, tamanoOriginalPt);
        if (texto.length() <= capacidad) {
            return tamanoOriginalPt;
        }
        double escalado = tamanoOriginalPt * capacidad / texto.length();
        return (short) Math.max(TAMANO_MINIMO_PT, Math.floor(escalado));
    }

    /**
     * Ajusta la fuente de la celda a su contenido. "Cabe" y "se puede bajar
     * más el tamaño" son preguntas independientes: si el texto no cabe pero
     * la fuente ya está en el suelo (o por debajo), el tamaño no cambia pero
     * igualmente se marca shrinkToFit — es la red de seguridad para ese
     * caso, y si no se marcara aquí la celda se quedaría desbordada en
     * silencio. Si el texto cabe, no se toca nada.
     *
     * Excepción: si el estilo original ya tiene wrapText, shrinkToFit no se
     * marca porque Excel lo ignora en ese caso (ver javadoc de la clase); la
     * única protección que queda ahí es el tamaño calculado.
     */
    public void ajustar(Cell celda) {
        if (celda.getCellType() != CellType.STRING) {
            return;
        }
        XSSFCellStyle original = (XSSFCellStyle) celda.getCellStyle();
        short tamanoOriginal = original.getFont().getFontHeightInPoints();
        String texto = celda.getStringCellValue();
        double anchoEnChars = celda.getSheet().getColumnWidth(celda.getColumnIndex()) / 256.0;
        if (cabe(texto, anchoEnChars, tamanoOriginal)) {
            return;
        }
        short nuevo = tamano(texto, anchoEnChars, tamanoOriginal);
        celda.setCellStyle(estiloCon(original, nuevo));
    }

    /** Si el texto entra en una columna de ese ancho al tamaño dado. */
    private static boolean cabe(String texto, double anchoEnChars, short tamanoPt) {
        if (texto == null || texto.isBlank()) {
            return true;
        }
        return texto.length() <= capacidad(anchoEnChars, tamanoPt);
    }

    /** Caracteres que caben en una columna de ese ancho a ese tamaño de fuente. */
    private static double capacidad(double anchoEnChars, short tamanoPt) {
        return anchoEnChars * TAMANO_DE_REFERENCIA_PT / tamanoPt;
    }

    /**
     * El estilo original con otro tamaño de fuente, creado una sola vez.
     * shrinkToFit solo se marca si el original no tiene wrapText: con
     * wrapText, Excel ignora shrinkToFit (ver javadoc de la clase) y
     * marcarlo sería un atributo inerte que además engaña sobre si la red de
     * seguridad está activa.
     */
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
            if (!original.getWrapText()) {
                estilo.setShrinkToFit(true);
            }
            return estilo;
        });
    }
}

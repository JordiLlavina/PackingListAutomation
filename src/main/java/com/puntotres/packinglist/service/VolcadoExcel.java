package com.puntotres.packinglist.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

/**
 * El volcado de un excel a texto que se le manda a Claude: lo usan la
 * conversión del excel del taller y la lectura de los pedidos de SMS de los
 * clientes sin lector propio. Cada fila lleva la dirección de sus celdas
 * ("B7=valor"), así que el modelo puede citar la fila en la que duda.
 */
public final class VolcadoExcel {

    private VolcadoExcel() {
    }

    /**
     * Un libro entero como texto, para mandárselo a Claude: cada hoja con su nombre, sus celdas
     * combinadas, y cada fila con sus celdas no vacías, "B7=valor", con las
     * fórmulas ya calculadas y los números como los enseña Excel. Con la
     * dirección de cada celda el modelo puede decir en qué fila duda y ver
     * qué columnas van juntas, que es justo lo que en un formato de taller no
     * se sabe de antemano.
     *
     * Lo que no es el valor y sí es información del taller se anota: una
     * celda tachada (~~valor~~: lo tachado no existe), un fondo de color
     * ([fondo #RRGGBB]), una letra de color ([letra #RRGGBB]: en Libro1.xlsx
     * las cajas que van mal están escritas en rojo y sin fondo, medido) y un
     * comentario ([nota: ...]). Sin esto, la única señal de "esta fila no va"
     * se perdería en el volcado.
     */
    public static String texto(byte[] excel, String encabezado, int maximoFilasPorHoja, int maximoColumnas)
            throws IOException {
        StringBuilder texto = new StringBuilder();
        texto.append(encabezado).append('\n');
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            DataFormatter formato = new DataFormatter();
            FormulaEvaluator evaluador = libro.getCreationHelper().createFormulaEvaluator();
            for (int h = 0; h < libro.getNumberOfSheets(); h++) {
                Sheet hoja = libro.getSheetAt(h);
                texto.append("\n=== Hoja ").append(h + 1).append(" de ")
                        .append(libro.getNumberOfSheets()).append(": \"")
                        .append(hoja.getSheetName()).append('"');
                if (libro.isSheetHidden(h) || libro.isSheetVeryHidden(h)) {
                    texto.append(" (oculta)");
                }
                texto.append(" ===\n");
                if (!hoja.getMergedRegions().isEmpty()) {
                    texto.append("(celdas combinadas: ").append(String.join(", ",
                            hoja.getMergedRegions().stream()
                                    .map(CellRangeAddress::formatAsString).toList()))
                            .append(")\n");
                }
                volcarHoja(hoja, formato, evaluador, texto, maximoFilasPorHoja, maximoColumnas);
            }
        }
        return texto.toString();
    }

    private static void volcarHoja(Sheet hoja, DataFormatter formato, FormulaEvaluator evaluador,
                                   StringBuilder texto, int maximoFilasPorHoja, int maximoColumnas) {
        int filasEscritas = 0;
        for (Row fila : hoja) {
            List<String> celdas = new ArrayList<>();
            for (Cell celda : fila) {
                if (celda.getColumnIndex() >= maximoColumnas) {
                    break;
                }
                String valor = valorDe(celda, formato, evaluador);
                if (valor.isBlank()) {
                    continue;
                }
                valor = valor.replace("\r", "").replace("\n", " / ").trim();
                celdas.add(new CellReference(celda).formatAsString(false) + "="
                        + anotar(celda, valor));
            }
            if (celdas.isEmpty()) {
                continue;
            }
            if (++filasEscritas > maximoFilasPorHoja) {
                texto.append("(hoja recortada: tiene más de ").append(maximoFilasPorHoja)
                        .append(" filas con datos)\n");
                return;
            }
            texto.append(String.join(" | ", celdas)).append('\n');
        }
        if (filasEscritas == 0) {
            texto.append("(vacía)\n");
        }
    }

    /** Tachado, fondo y comentario de una celda, pegados a su valor. */
    private static String anotar(Cell celda, String valor) {
        StringBuilder anotado = new StringBuilder();
        CellStyle estilo = celda.getCellStyle();
        boolean tachado = false;
        String letra = null;
        if (estilo != null) {
            try {
                Font fuente = celda.getSheet().getWorkbook().getFontAt(estilo.getFontIndex());
                tachado = fuente != null && fuente.getStrikeout();
                letra = letraDe(fuente);
            } catch (RuntimeException e) {
                tachado = false;
            }
        }
        anotado.append(tachado ? "~~" + valor + "~~" : valor);
        String fondo = fondoDe(estilo);
        if (fondo != null) {
            anotado.append(" [fondo ").append(fondo).append(']');
        }
        if (letra != null) {
            anotado.append(" [letra ").append(letra).append(']');
        }
        Comment comentario = celda.getCellComment();
        if (comentario != null && comentario.getString() != null) {
            String nota = comentario.getString().getString();
            if (nota != null && !nota.isBlank()) {
                anotado.append(" [nota: ").append(nota.replace("\n", " ").trim()).append(']');
            }
        }
        return anotado.toString();
    }

    /**
     * "#RRGGBB" de la letra, o null si es la de siempre: negra, automática o
     * de un libro que no es xlsx. Solo se anota la que se sale de lo normal,
     * que es la que el taller ha elegido para decir algo.
     */
    private static String letraDe(Font fuente) {
        if (!(fuente instanceof XSSFFont xssf) || xssf.getXSSFColor() == null) {
            return null;
        }
        String argb = xssf.getXSSFColor().getARGBHex();
        if (argb == null || argb.length() < 6) {
            return null;
        }
        String rgb = argb.substring(argb.length() - 6).toUpperCase();
        return "000000".equals(rgb) ? null : "#" + rgb;
    }

    /** "#RRGGBB" del relleno de la celda, o null si no tiene o es blanco. */
    private static String fondoDe(CellStyle estilo) {
        if (estilo == null || estilo.getFillPattern() == FillPatternType.NO_FILL) {
            return null;
        }
        if (estilo.getFillForegroundColorColor() instanceof XSSFColor color) {
            String argb = color.getARGBHex();
            if (argb == null || argb.length() < 6) {
                return null;
            }
            String rgb = argb.substring(argb.length() - 6).toUpperCase();
            return "FFFFFF".equals(rgb) ? null : "#" + rgb;
        }
        return null;
    }

    /**
     * El valor como lo enseña Excel. Una fórmula que POI no sabe calcular
     * (una función que no implementa, un enlace a otro libro) se queda con el
     * último valor que guardó Excel, que es lo que vio quien la escribió.
     */
    private static String valorDe(Cell celda, DataFormatter formato, FormulaEvaluator evaluador) {
        try {
            String valor = formato.formatCellValue(celda, evaluador);
            return valor == null ? "" : valor;
        } catch (RuntimeException e) {
            if (celda.getCellType() == CellType.FORMULA) {
                try {
                    return celda.getCachedFormulaResultType() == CellType.NUMERIC
                            ? formato.formatRawCellContents(celda.getNumericCellValue(),
                                    celda.getCellStyle().getDataFormat(),
                                    celda.getCellStyle().getDataFormatString())
                            : celda.getStringCellValue();
                } catch (RuntimeException otra) {
                    return "";
                }
            }
            return "";
        }
    }
}

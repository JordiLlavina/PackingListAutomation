package com.puntotres.packinglist.service.etiquetas;

import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;

/**
 * Anclaje de imagen de tamaño fijo, equivalente al oneCellAnchor que
 * escribe Excel: la imagen arranca en (columna, fila) más un desplazamiento
 * en EMU y ocupa (cx, cy) EMU, sin estirarse si cambian filas o columnas.
 *
 * POI no expone oneCellAnchor desde createPicture, así que se construye un
 * anclaje de dos celdas cuyo extremo se calcula recorriendo los anchos y
 * altos reales de la hoja, con AnchorType.MOVE_DONT_RESIZE.
 *
 * Lo usan los dos builders de etiquetas de AMI (caja y artículo).
 */
public final class AnclajeImagen {

    private AnclajeImagen() {
    }

    public static XSSFClientAnchor fijo(Sheet hoja, int columna, long dx,
                                        int fila, long dy, long cx, long cy) {
        int col2 = columna;
        long xRestante = dx + cx;
        while (xRestante > anchoColumnaEmu(hoja, col2)) {
            long ancho = anchoColumnaEmu(hoja, col2);
            // Una columna de ancho 0 (oculta) haría que el bucle no terminase nunca.
            if (ancho <= 0) {
                break;
            }
            xRestante -= ancho;
            col2++;
        }
        int fila2 = fila;
        long yRestante = dy + cy;
        while (yRestante > altoFilaEmu(hoja, fila2)) {
            long alto = altoFilaEmu(hoja, fila2);
            // Una fila de alto 0 (oculta) haría que el bucle no terminase nunca.
            if (alto <= 0) {
                break;
            }
            yRestante -= alto;
            fila2++;
        }
        XSSFClientAnchor ancla = new XSSFClientAnchor((int) dx, (int) dy,
                (int) xRestante, (int) yRestante, columna, fila, col2, fila2);
        ancla.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
        return ancla;
    }

    private static long anchoColumnaEmu(Sheet hoja, int columna) {
        return Units.columnWidthToEMU(hoja.getColumnWidth(columna));
    }

    private static long altoFilaEmu(Sheet hoja, int fila) {
        float puntos = hoja.getRow(fila) != null
                ? hoja.getRow(fila).getHeightInPoints()
                : hoja.getDefaultRowHeightInPoints();
        return Units.toEMU(puntos);
    }
}

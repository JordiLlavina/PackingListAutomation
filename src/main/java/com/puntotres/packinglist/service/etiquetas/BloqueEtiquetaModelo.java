package com.puntotres.packinglist.service.etiquetas;

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;

/**
 * El bloque de etiquetas modelo de una plantilla: valores, estilos, altos
 * de fila y celdas combinadas de las primeras {@code altura} filas,
 * capturados antes de escribir nada para poder replicarlos por caja o por
 * palet. Compartido por los builders de etiquetas (AMI, APC).
 */
record BloqueEtiquetaModelo(List<FilaModelo> filas, List<CellRangeAddress> merges,
                            int altura) {

    record CeldaModelo(int col, CellStyle estilo, CellType tipo, String texto) {
    }

    record FilaModelo(int fila, float altoPuntos, boolean altoPersonalizado,
                      List<CeldaModelo> celdas) {
    }

    /** El bloque modelo arranca en la primera fila de la hoja. */
    static BloqueEtiquetaModelo capturar(XSSFSheet hoja, int altura) {
        return capturar(hoja, 0, altura);
    }

    /**
     * El bloque modelo arranca en filaInicio. Las hojas de etiquetas de palet
     * de AMI llevan encima una fila con un contador apuntado a mano, así que
     * su bloque empieza en la fila 1 y esa primera fila no se replica.
     */
    static BloqueEtiquetaModelo capturar(XSSFSheet hoja, int filaInicio, int altura) {
        List<FilaModelo> filas = new ArrayList<>();
        for (int i = 0; i < altura; i++) {
            Row fila = hoja.getRow(filaInicio + i);
            if (fila == null) {
                continue;
            }
            List<CeldaModelo> celdas = new ArrayList<>();
            for (Cell celda : fila) {
                celdas.add(new CeldaModelo(celda.getColumnIndex(), celda.getCellStyle(),
                        celda.getCellType(),
                        celda.getCellType() == CellType.STRING
                                ? celda.getStringCellValue() : null));
            }
            // La fila se guarda RELATIVA al inicio del bloque: así copiarEn
            // recibe siempre la fila absoluta donde va la copia entera.
            filas.add(new FilaModelo(i, fila.getHeightInPoints(),
                    ((XSSFRow) fila).getCTRow().getCustomHeight(), celdas));
        }
        List<CellRangeAddress> merges = new ArrayList<>();
        for (CellRangeAddress merge : hoja.getMergedRegions()) {
            if (merge.getFirstRow() >= filaInicio
                    && merge.getLastRow() < filaInicio + altura) {
                merges.add(new CellRangeAddress(merge.getFirstRow() - filaInicio,
                        merge.getLastRow() - filaInicio,
                        merge.getFirstColumn(), merge.getLastColumn()));
            }
        }
        return new BloqueEtiquetaModelo(filas, merges, altura);
    }

    void copiarEn(XSSFSheet hoja, int filaDestino) {
        for (FilaModelo modelo : filas) {
            XSSFRow fila = hoja.createRow(filaDestino + modelo.fila());
            // Se aplica siempre (no solo si customHeight="1" en el XML
            // original): algunas plantillas (APC) traen ht explícito sin
            // marcar ese flag y aun así hay que preservar el alto real.
            fila.setHeightInPoints(modelo.altoPuntos());
            for (CeldaModelo celdaModelo : modelo.celdas()) {
                Cell celda = fila.createCell(celdaModelo.col());
                // Mismo libro: la referencia de estilo se comparte, sin clonar.
                celda.setCellStyle(celdaModelo.estilo());
                if (celdaModelo.texto() != null) {
                    celda.setCellValue(celdaModelo.texto());
                }
            }
        }
        for (CellRangeAddress merge : merges) {
            hoja.addMergedRegion(new CellRangeAddress(
                    merge.getFirstRow() + filaDestino, merge.getLastRow() + filaDestino,
                    merge.getFirstColumn(), merge.getLastColumn()));
        }
    }
}

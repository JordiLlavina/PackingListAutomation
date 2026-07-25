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

    static BloqueEtiquetaModelo capturar(XSSFSheet hoja, int altura) {
        List<FilaModelo> filas = new ArrayList<>();
        for (int i = 0; i < altura; i++) {
            Row fila = hoja.getRow(i);
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
            filas.add(new FilaModelo(i, fila.getHeightInPoints(),
                    ((XSSFRow) fila).getCTRow().getCustomHeight(), celdas));
        }
        List<CellRangeAddress> merges = new ArrayList<>();
        for (CellRangeAddress merge : hoja.getMergedRegions()) {
            if (merge.getLastRow() < altura) {
                merges.add(merge);
            }
        }
        return new BloqueEtiquetaModelo(filas, merges, altura);
    }

    void copiarEn(XSSFSheet hoja, int filaDestino) {
        for (FilaModelo modelo : filas) {
            XSSFRow fila = hoja.createRow(filaDestino + modelo.fila());
            if (modelo.altoPersonalizado()) {
                fila.setHeightInPoints(modelo.altoPuntos());
            }
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

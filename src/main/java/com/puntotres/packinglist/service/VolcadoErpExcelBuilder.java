package com.puntotres.packinglist.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import com.puntotres.packinglist.model.VolcadoErpData;
import com.puntotres.packinglist.model.VolcadoErpLinea;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Escribe el excel de volcado de albarán con el formato que importa
 * ICSuite: las ocho primeras columnas (A-H) son las suyas y van en ese
 * orden exacto; COLOR y TALLA se añaden al final solo para poder revisar
 * el fichero a ojo (el ERP no las lee).
 *
 * Los tipos de celda son los del fichero de referencia
 * (docs/Volcado Albaran ERP/): números en Lin., Comanda, Quantitat y los
 * dos descuentos, pero texto en Import Div. ("0.00").
 */
@Service
public class VolcadoErpExcelBuilder {

    private static final String[] HEADERS = {
        "Lin.", "Article", "Comanda", "Quantitat", "Uni",
        "% Dte 1", "% Dte 2", "Import Div.", "COLOR", "TALLA"
    };

    /** Import Div.: cero fijo y como texto, igual que en el fichero de referencia. */
    private static final String IMPORT_DIV = "0.00";

    public byte[] generar(VolcadoErpData data) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Volcado");

        // Crear fila de encabezados
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 15 * 256);  // 15 caracteres de ancho
        }

        // Crear filas de datos
        int rowNum = 1;
        for (VolcadoErpLinea linea : data.getLineas()) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(linea.getNumeroLinea());
            escribirTexto(row, 1, linea.getArticle());
            escribirComanda(row, linea.getComanda());
            row.createCell(3).setCellValue(linea.getQuantitat());
            escribirTexto(row, 4, linea.getUni());
            row.createCell(5).setCellValue(0);
            row.createCell(6).setCellValue(0);
            row.createCell(7).setCellValue(IMPORT_DIV);
            escribirTexto(row, 8, linea.getColor());
            escribirTexto(row, 9, linea.getTalla());
        }

        // Exportar a byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return outputStream.toByteArray();
    }

    /** Un dato que falta deja la celda vacía, nunca escribe "null". */
    private static void escribirTexto(Row row, int columna, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        row.createCell(columna).setCellValue(valor);
    }

    /**
     * La comanda la teclea el usuario: si es un número va como número (que
     * es lo que espera ICSuite) y si no, tal cual como texto. Sin comanda,
     * la celda se queda vacía y el resto de la línea se escribe igual.
     */
    private static void escribirComanda(Row row, String comanda) {
        if (comanda == null || comanda.isBlank()) {
            return;
        }
        try {
            row.createCell(2).setCellValue(Long.parseLong(comanda.trim()));
        } catch (NumberFormatException e) {
            row.createCell(2).setCellValue(comanda.trim());
        }
    }
}

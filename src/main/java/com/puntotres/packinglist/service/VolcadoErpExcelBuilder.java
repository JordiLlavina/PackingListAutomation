package com.puntotres.packinglist.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import com.puntotres.packinglist.model.VolcadoErpData;
import com.puntotres.packinglist.model.VolcadoErpLinea;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class VolcadoErpExcelBuilder {

    private static final String[] HEADERS = {
        "ARTICLE", "TALLA", "COLORCODI", "COLOR", "SISTALL", "SISGRUP", "QUANTITAT"
    };

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

            row.createCell(0).setCellValue(linea.getArticle());
            row.createCell(1).setCellValue(linea.getTalla());
            row.createCell(2).setCellValue(linea.getColorCodi());
            row.createCell(3).setCellValue(linea.getColor());
            row.createCell(4).setCellValue(linea.getSistall());
            row.createCell(5).setCellValue(linea.getSisgrup());
            row.createCell(6).setCellValue(linea.getQuantitat());
        }

        // Exportar a byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return outputStream.toByteArray();
    }
}

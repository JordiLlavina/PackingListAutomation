package com.puntotres.packinglist.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.puntotres.packinglist.model.*;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.util.*;

public class VolcadoErpExcelBuilderTest {

    @Test
    public void testEscribeExcelConColumnasCorrectas() throws IOException {
        VolcadoErpExcelBuilder builder = new VolcadoErpExcelBuilder();

        List<VolcadoErpLinea> lineas = Arrays.asList(
            new VolcadoErpLinea("USL728.AL217", "80", "001", "NOIR", 1, 1, 150),
            new VolcadoErpLinea("UBL029.AL0216", "90", "002", "BLEU", 1, 1, 45)
        );

        VolcadoErpData data = new VolcadoErpData(lineas, "test.xlsx");
        byte[] excelContent = builder.generar(data);

        assertNotNull(excelContent);
        assertTrue(excelContent.length > 0);

        // Verificar contenido: abrir el xlsx y validar columnas
        Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(excelContent));
        Sheet sheet = wb.getSheetAt(0);
        Row headerRow = sheet.getRow(0);

        assertEquals("ARTICLE", headerRow.getCell(0).getStringCellValue());
        assertEquals("TALLA", headerRow.getCell(1).getStringCellValue());
        assertEquals("COLORCODI", headerRow.getCell(2).getStringCellValue());
        assertEquals("COLOR", headerRow.getCell(3).getStringCellValue());
        assertEquals("SISTALL", headerRow.getCell(4).getStringCellValue());
        assertEquals("SISGRUP", headerRow.getCell(5).getStringCellValue());
        assertEquals("QUANTITAT", headerRow.getCell(6).getStringCellValue());

        // Verificar primera fila de datos
        Row dataRow = sheet.getRow(1);
        assertEquals("USL728.AL217", dataRow.getCell(0).getStringCellValue());
        assertEquals("80", dataRow.getCell(1).getStringCellValue());
        assertEquals("001", dataRow.getCell(2).getStringCellValue());
        assertEquals("NOIR", dataRow.getCell(3).getStringCellValue());
        assertEquals(1, dataRow.getCell(4).getNumericCellValue());
        assertEquals(150, dataRow.getCell(6).getNumericCellValue());

        wb.close();
    }
}

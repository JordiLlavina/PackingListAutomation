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
            new VolcadoErpLinea(1, "USL728.AL217", "12345", 150, "U", "NOIR", null),
            new VolcadoErpLinea(2, "UBL029.AL0216", "12345", 45, "90", "BLEU", "90")
        );

        VolcadoErpData data = new VolcadoErpData(lineas, "test.xlsx");
        byte[] excelContent = builder.generar(data);

        assertNotNull(excelContent);
        assertTrue(excelContent.length > 0);

        // Verificar contenido: abrir el xlsx y validar columnas
        Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(excelContent));
        Sheet sheet = wb.getSheetAt(0);
        Row headerRow = sheet.getRow(0);

        assertEquals("Lin.", headerRow.getCell(0).getStringCellValue());
        assertEquals("Article", headerRow.getCell(1).getStringCellValue());
        assertEquals("Comanda", headerRow.getCell(2).getStringCellValue());
        assertEquals("Quantitat", headerRow.getCell(3).getStringCellValue());
        assertEquals("Uni", headerRow.getCell(4).getStringCellValue());
        assertEquals("% Dte 1", headerRow.getCell(5).getStringCellValue());
        assertEquals("% Dte 2", headerRow.getCell(6).getStringCellValue());
        assertEquals("Import Div.", headerRow.getCell(7).getStringCellValue());
        assertEquals("COLOR", headerRow.getCell(8).getStringCellValue());
        assertEquals("TALLA", headerRow.getCell(9).getStringCellValue());

        // Primera fila de datos: bolso (Uni = U) y talla vacía
        Row dataRow = sheet.getRow(1);
        assertEquals(1, dataRow.getCell(0).getNumericCellValue());
        assertEquals("USL728.AL217", dataRow.getCell(1).getStringCellValue());
        assertEquals(12345, dataRow.getCell(2).getNumericCellValue());
        assertEquals(150, dataRow.getCell(3).getNumericCellValue());
        assertEquals("U", dataRow.getCell(4).getStringCellValue());
        assertEquals(0, dataRow.getCell(5).getNumericCellValue());
        assertEquals(0, dataRow.getCell(6).getNumericCellValue());
        // Import Div. va como texto, igual que en el fichero de referencia
        assertEquals("0.00", dataRow.getCell(7).getStringCellValue());
        assertEquals("NOIR", dataRow.getCell(8).getStringCellValue());
        assertNull(dataRow.getCell(9));

        // Segunda fila: cinturón, la unidad es su talla
        Row filaCinturon = sheet.getRow(2);
        assertEquals("90", filaCinturon.getCell(4).getStringCellValue());
        assertEquals("90", filaCinturon.getCell(9).getStringCellValue());

        wb.close();
    }

    @Test
    public void testSinComandaLaCeldaQuedaVaciaYElRestoSeEscribeIgual() throws IOException {
        VolcadoErpExcelBuilder builder = new VolcadoErpExcelBuilder();

        VolcadoErpData data = new VolcadoErpData(List.of(
                new VolcadoErpLinea(1, "USL728.AL217", null, 150, "U", "NOIR", null)),
                "test.xlsx");

        Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(builder.generar(data)));
        Row dataRow = wb.getSheetAt(0).getRow(1);

        assertNull(dataRow.getCell(2), "Sin comanda la celda no se escribe");
        assertEquals(150, dataRow.getCell(3).getNumericCellValue());

        wb.close();
    }

    @Test
    public void testComandaNoNumericaSeEscribeComoTexto() throws IOException {
        VolcadoErpExcelBuilder builder = new VolcadoErpExcelBuilder();

        VolcadoErpData data = new VolcadoErpData(List.of(
                new VolcadoErpLinea(1, "USL728.AL217", "CMD-2026/1", 150, "U", "NOIR", null)),
                "test.xlsx");

        Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(builder.generar(data)));
        Row dataRow = wb.getSheetAt(0).getRow(1);

        assertEquals("CMD-2026/1", dataRow.getCell(2).getStringCellValue());

        wb.close();
    }
}

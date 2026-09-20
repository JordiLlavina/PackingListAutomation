package com.puntotres.packinglist.actividad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.seguridad.Seccion;

/**
 * Como el resto de builders del proyecto: se reabre el .xlsx generado con POI
 * y se comprueban celdas reales.
 */
class ActividadExcelBuilderTest {

    @Test
    void elExcelTraeUnaHojaPorBloqueYLasCeldasEnSuSitio() throws Exception {
        ResumenActividad resumen = new ResumenActividad(
                2, 5, 9, 2,
                List.of(new ResumenActividad.LineaUsuario("ana@puntotres.com", null, 3, 7)),
                List.of(new ResumenActividad.LineaSeccion(Seccion.PACKING_LIST, 7)),
                List.of(new ResumenActividad.LineaCliente("AMI", 4, 2)),
                List.of());
        FiltroActividad filtro = new FiltroActividad(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null);

        byte[] bytes = new ActividadExcelBuilder().construir(resumen, filtro);

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(4, libro.getNumberOfSheets());

            Sheet resumenHoja = libro.getSheet("Resumen");
            assertTrue(resumenHoja.getRow(0).getCell(0).getStringCellValue().contains("01/09/2026"));

            Sheet porCliente = libro.getSheet("Por cliente");
            assertEquals("AMI", porCliente.getRow(1).getCell(0).getStringCellValue());
            assertEquals(4.0, porCliente.getRow(1).getCell(1).getNumericCellValue());
        }
    }
}

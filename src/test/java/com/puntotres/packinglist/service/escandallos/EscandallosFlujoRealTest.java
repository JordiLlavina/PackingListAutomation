package com.puntotres.packinglist.service.escandallos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Flujo completo con los dos escandallos REALES del ERP, y deja el excel en
 * target/ para abrirlo a mano, igual que hacen los flujos de packing list y de
 * etiquetas de artículo.
 */
class EscandallosFlujoRealTest {

    private final EscandallosGenerationService service =
            new EscandallosGenerationService(new EscandalloReader(), new EscandallosExcelBuilder());

    private static FicheroEscandallo recurso(String nombre) throws Exception {
        String ruta = "/ejemplos/escandallos/" + nombre;
        try (InputStream entrada = EscandallosFlujoRealTest.class.getResourceAsStream(ruta)) {
            assertNotNull(entrada, "falta el recurso de test " + ruta);
            return new FicheroEscandallo(nombre, entrada.readAllBytes());
        }
    }

    @Test
    void losDosEscandallosRealesGeneranUnExcelAbribleYLoDejaEnTarget() throws Exception {
        ExcelEscandallos excel = service.procesar(
                List.of(recurso("ULL770 NOIR.xlsx"), recurso("ULL770 SAND.xlsx")));

        // Para inspección manual: abrirlo y comparar con
        // docs/Procesado Escandallos ICSUITE/escandallos_extraidos_ejemplo.xlsx
        Files.write(Path.of("target", "Escandallos ICSUITE.xlsx"), excel.contenido());

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel.contenido()))) {
            assertEquals(2, libro.getNumberOfSheets());

            Sheet noir = libro.getSheet("ULL770.AL245 NOIR");
            assertEquals("ULL770.AL245", noir.getRow(1).getCell(1).getStringCellValue());
            assertEquals("SAC CANDY RABAT LARGE UNISEX",
                    noir.getRow(2).getCell(1).getStringCellValue());
            assertEquals("000    0014 NOIR", noir.getRow(3).getCell(1).getStringCellValue());
            // 25 materiales a partir de la fila 7 (índice 6).
            assertEquals(30, noir.getLastRowNum());
            assertEquals("P-FOUB01", noir.getRow(6).getCell(0).getStringCellValue());
            assertEquals(1.2, noir.getRow(6).getCell(2).getNumericCellValue(), 0.0001);
            assertEquals("R-TT00", noir.getRow(30).getCell(0).getStringCellValue());
            assertEquals(0.008, noir.getRow(30).getCell(2).getNumericCellValue(), 0.0001);

            Sheet sand = libro.getSheet("ULL770.AL245 SABLE SAND");
            assertEquals("001    255 SABLE SAND", sand.getRow(3).getCell(1).getStringCellValue());
            assertEquals("P-FOUB255", sand.getRow(6).getCell(0).getStringCellValue());
            assertEquals("PIEL/FOULARD SAND", sand.getRow(6).getCell(1).getStringCellValue());
        }
        assertTrue(excel.avisos().isEmpty(), excel.avisos().toString());
    }
}

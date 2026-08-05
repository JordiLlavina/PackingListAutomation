package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * El bloque modelo de una plantilla no siempre empieza en la primera fila:
 * las hojas de etiquetas de palet de AMI llevan encima una fila con un
 * contador apuntado a mano, así que el bloque arranca en la fila 1.
 */
class BloqueEtiquetaModeloTest {

    @Test
    void capturarDesdeUnaFilaCopiaSoloEseBloque() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet();
            hoja.createRow(0).createCell(0).setCellValue("contador");
            hoja.createRow(1).createCell(0).setCellValue("primera");
            hoja.createRow(2).createCell(0).setCellValue("segunda");

            BloqueEtiquetaModelo modelo = BloqueEtiquetaModelo.capturar(hoja, 1, 2);
            modelo.copiarEn(hoja, 3);

            assertEquals("primera", hoja.getRow(3).getCell(0).getStringCellValue());
            assertEquals("segunda", hoja.getRow(4).getCell(0).getStringCellValue());
        }
    }

    @Test
    void lasCeldasCombinadasSeCopianRelativasAlInicioDelBloque() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet();
            for (int i = 0; i < 3; i++) {
                hoja.createRow(i).createCell(0);
            }
            hoja.addMergedRegion(new CellRangeAddress(1, 2, 0, 1));

            BloqueEtiquetaModelo.capturar(hoja, 1, 2).copiarEn(hoja, 3);

            assertTrue(hoja.getMergedRegions().contains(new CellRangeAddress(3, 4, 0, 1)),
                    "la combinada del bloque no se ha copiado en su sitio: "
                            + hoja.getMergedRegions());
        }
    }

    @Test
    void capturarSinFilaInicioSigueLeyendoDesdeLaPrimera() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet();
            hoja.createRow(0).createCell(0).setCellValue("primera");
            hoja.createRow(1).createCell(0).setCellValue("segunda");

            BloqueEtiquetaModelo.capturar(hoja, 2).copiarEn(hoja, 2);

            assertEquals("primera", hoja.getRow(2).getCell(0).getStringCellValue());
            assertEquals("segunda", hoja.getRow(3).getCell(0).getStringCellValue());
        }
    }
}

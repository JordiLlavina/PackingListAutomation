package com.puntotres.packinglist.service.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.catalogo.ExcelColoresPieles.Fila;
import com.puntotres.packinglist.service.catalogo.ExcelColoresPieles.Leido;

class ExcelColoresPielesTest {

    @Test
    void loExportadoSeVuelveALeerIgualConUnaHojaPorTabla() throws Exception {
        byte[] excel = ExcelColoresPieles.escribir("APC",
                List.of(new Fila("LZZ", "NOIR"), new Fila("CAB", "")),
                List.of(new Fila("PXCBC", "VACHETTE")));

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet colores = libro.getSheet("Colores");
            assertEquals("Cliente", colores.getRow(0).getCell(0).getStringCellValue());
            assertEquals("APC", colores.getRow(0).getCell(1).getStringCellValue());
            assertEquals("Código", colores.getRow(2).getCell(0).getStringCellValue());
            assertEquals("LZZ", colores.getRow(3).getCell(0).getStringCellValue());
            assertEquals("@", colores.getColumnStyle(0).getDataFormatString(),
                    "el código en texto también en las filas nuevas");
            assertEquals("Ref. piel", libro.getSheet("Pieles").getRow(2).getCell(0).getStringCellValue());
        }

        Leido leido = ExcelColoresPieles.leer(excel);
        assertEquals("APC", leido.cliente());
        assertEquals(Map.of("LZZ", "NOIR", "CAB", ""), leido.colores());
        assertEquals(Map.of("PXCBC", "VACHETTE"), leido.pieles());
    }

    @Test
    void lasFilasAnadidasAManoSeLeenNormalizadasYUnCodigoNumericoConservaSusCeros() throws Exception {
        byte[] excel = ExcelColoresPieles.escribir("AMI", List.of(new Fila("001", "")), List.of());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel));
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet colores = libro.getSheet("Colores");
            colores.getRow(3).getCell(1).setCellValue("  azul marino ");
            Row nueva = colores.createRow(4);
            nueva.createCell(0).setCellValue(" z9 ");
            nueva.createCell(1).setCellValue("Verde");
            Row numerica = colores.createRow(6); // con una fila vacía en medio
            numerica.createCell(0).setCellValue(42);
            numerica.createCell(1).setCellValue("ROJO");
            libro.write(salida);
            excel = salida.toByteArray();
        }

        Map<String, String> esperado = new LinkedHashMap<>();
        esperado.put("001", "azul marino");
        esperado.put("Z9", "Verde");
        esperado.put("42", "ROJO");
        assertEquals(esperado, ExcelColoresPieles.leer(excel).colores());
    }

    @Test
    void unaHojaQueNoVieneEsNulaYSinNingunaNoEsElExcelDeLaPantalla() throws Exception {
        byte[] soloColores;
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                ExcelColoresPieles.escribir("LGN", List.of(new Fila("X", "Y")), List.of())));
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.removeSheetAt(libro.getSheetIndex("Pieles"));
            libro.write(salida);
            soloColores = salida.toByteArray();
        }
        Leido leido = ExcelColoresPieles.leer(soloColores);
        assertEquals(Map.of("X", "Y"), leido.colores());
        assertNull(leido.pieles());

        byte[] otro;
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.createSheet("Hoja1");
            libro.write(salida);
            otro = salida.toByteArray();
        }
        IllegalArgumentException sinHojas = assertThrows(IllegalArgumentException.class,
                () -> ExcelColoresPieles.leer(otro));
        assertTrue(sinHojas.getMessage().contains("esta pantalla"), sinHojas.getMessage());
        IllegalArgumentException noExcel = assertThrows(IllegalArgumentException.class,
                () -> ExcelColoresPieles.leer("hola".getBytes()));
        assertTrue(noExcel.getMessage().contains(".xlsx"), noExcel.getMessage());
    }
}

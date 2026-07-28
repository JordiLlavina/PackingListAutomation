package com.puntotres.packinglist.service.escandallos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * El libro de salida calca escandallos_extraidos_ejemplo.xlsx: cabecera en
 * A2:B4, encabezados de tabla en la fila 6 y los materiales a partir de la 7.
 */
class EscandallosExcelBuilderTest {

    private final EscandallosExcelBuilder builder = new EscandallosExcelBuilder();

    private static Escandallo noir() {
        return new Escandallo("ULL770.AL245", "SAC CANDY RABAT LARGE UNISEX", "000    0014 NOIR",
                List.of(new LineaEscandallo("P-FOUB01", "PIEL/FOULARD NEGRO", 1.2),
                        new LineaEscandallo("F-ECAMI", "ETIQUETA EXTERIOR AMI", 1.0)),
                "ULL770 NOIR.xlsx");
    }

    private static Escandallo sand() {
        return new Escandallo("ULL770.AL245", "SAC CANDY RABAT LARGE UNISEX",
                "001    255 SABLE SAND",
                List.of(new LineaEscandallo("P-FOUB255", "PIEL/FOULARD SAND", 1.2)),
                "ULL770 SAND.xlsx");
    }

    private static String texto(Sheet hoja, int fila, int columna) {
        Cell celda = hoja.getRow(fila).getCell(columna);
        return celda == null ? null : celda.getStringCellValue();
    }

    @Test
    void unaHojaPorEscandalloConSuModeloYSuColorDeNombre() throws Exception {
        ExcelEscandallos excel = builder.construir(List.of(noir(), sand()));

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel.contenido()))) {
            assertEquals(2, libro.getNumberOfSheets());
            assertEquals("ULL770.AL245 NOIR", libro.getSheetName(0));
            assertEquals("ULL770.AL245 SABLE SAND", libro.getSheetName(1));
        }
        assertEquals(List.of("ULL770.AL245 NOIR", "ULL770.AL245 SABLE SAND"), excel.hojas());
    }

    @Test
    void laCabeceraLlevaModeloDescripcionYColorEnteros() throws Exception {
        ExcelEscandallos excel = builder.construir(List.of(noir()));

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel.contenido()))) {
            Sheet hoja = libro.getSheetAt(0);
            assertEquals("MODEL", texto(hoja, 1, 0));
            assertEquals("ULL770.AL245", texto(hoja, 1, 1));
            assertEquals("DESCRIPCIÓ", texto(hoja, 2, 0));
            assertEquals("SAC CANDY RABAT LARGE UNISEX", texto(hoja, 2, 1));
            assertEquals("COLOR", texto(hoja, 3, 0));
            // El color va entero, con sus códigos: abreviarlo es cosa del
            // nombre de la hoja, no del dato.
            assertEquals("000    0014 NOIR", texto(hoja, 3, 1));
        }
    }

    @Test
    void laTablaEmpiezaConSusEncabezadosEnLaFilaSeis() throws Exception {
        ExcelEscandallos excel = builder.construir(List.of(noir()));

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel.contenido()))) {
            Sheet hoja = libro.getSheetAt(0);
            assertEquals("Article", texto(hoja, 5, 0));
            assertEquals("Descripció", texto(hoja, 5, 1));
            assertEquals("Quantitat", texto(hoja, 5, 2));
        }
    }

    @Test
    void cadaMaterialEsUnaFilaConLaCantidadComoNumero() throws Exception {
        ExcelEscandallos excel = builder.construir(List.of(noir()));

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel.contenido()))) {
            Sheet hoja = libro.getSheetAt(0);
            assertEquals("P-FOUB01", texto(hoja, 6, 0));
            assertEquals("PIEL/FOULARD NEGRO", texto(hoja, 6, 1));
            assertEquals(CellType.NUMERIC, hoja.getRow(6).getCell(2).getCellType());
            assertEquals(1.2, hoja.getRow(6).getCell(2).getNumericCellValue(), 0.0001);
            assertEquals("F-ECAMI", texto(hoja, 7, 0));
            assertEquals(7, hoja.getLastRowNum());
        }
    }

    @Test
    void unaCantidadDesconocidaDejaLaCeldaEnBlanco() throws Exception {
        Escandallo sinCantidad = new Escandallo("ULL999", "BOLSO", "000 NEGRO",
                List.of(new LineaEscandallo("R-ABO", "ABOAT 100gr ESPUMA", null)), "x.xlsx");

        ExcelEscandallos excel = builder.construir(List.of(sinCantidad));

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel.contenido()))) {
            Cell cantidad = libro.getSheetAt(0).getRow(6).getCell(2);
            assertTrue(cantidad == null || cantidad.getCellType() == CellType.BLANK,
                    "una cantidad desconocida no se escribe como 0");
        }
    }

    @Test
    void unEscandalloSinModeloDejaLaCeldaEnBlancoPeroGeneraSuHoja() throws Exception {
        Escandallo sinModelo = new Escandallo(null, null, null,
                List.of(new LineaEscandallo("R-ABO", "ABOAT", 1.0)), "raro.xlsx");

        ExcelEscandallos excel = builder.construir(List.of(sinModelo));

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel.contenido()))) {
            assertEquals("raro", libro.getSheetName(0));
            Cell modelo = libro.getSheetAt(0).getRow(1).getCell(1);
            assertTrue(modelo == null || modelo.getCellType() == CellType.BLANK, "MODEL vacío");
        }
    }

    @Test
    void elChoqueDeNombresDeHojaLlegaComoAviso() throws Exception {
        ExcelEscandallos excel = builder.construir(List.of(noir(), noir()));

        assertEquals(List.of("ULL770.AL245 NOIR", "ULL770.AL245 NOIR (2)"), excel.hojas());
        assertTrue(excel.avisos().stream().anyMatch(aviso -> aviso.contains("(2)")),
                excel.avisos().toString());
    }
}

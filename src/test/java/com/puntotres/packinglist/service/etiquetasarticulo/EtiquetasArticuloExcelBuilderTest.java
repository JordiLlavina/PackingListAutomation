package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class EtiquetasArticuloExcelBuilderTest {

    private final EtiquetasArticuloExcelBuilder builder = new EtiquetasArticuloExcelBuilder();

    private static final EtiquetaArticulo BOLSO = new EtiquetaArticulo(
            "USL738.AL0137", "Size: U", "A236 TRUFFLE", "Cde: 07714", "3666598543892");

    private static XSSFWorkbook reabrir(byte[] xlsx) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    }

    @Test
    void unaHojaPorEtiquetaConSuNombre() throws Exception {
        byte[] xlsx = builder.generar(List.of(
                new HojaEtiquetas("USL738.AL0137 TRUFFLE 07714CH", BOLSO),
                new HojaEtiquetas("ULL754.AL0218 A328 07683", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            assertEquals(2, libro.getNumberOfSheets());
            assertEquals("USL738.AL0137 TRUFFLE 07714CH", libro.getSheetName(0));
            assertEquals("ULL754.AL0218 A328 07683", libro.getSheetName(1));
        }
    }

    @Test
    void escribeLasCuatroCeldasDelPrimerBloque() throws Exception {
        byte[] xlsx = builder.generar(List.of(new HojaEtiquetas("HOJA", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            int base = EtiquetasArticuloExcelBuilder.filaBase(0);   // fila 0-based 1 = "2" en Excel
            assertEquals("USL738.AL0137", hoja.getRow(base).getCell(0).getStringCellValue());
            assertEquals("Size: U", hoja.getRow(base).getCell(1).getStringCellValue());
            assertEquals("A236 TRUFFLE", hoja.getRow(base + 1).getCell(0).getStringCellValue());
            assertEquals("Cde: 07714", hoja.getRow(base + 1).getCell(1).getStringCellValue());
        }
    }

    @Test
    void repiteLaEtiquetaEnLasCuatroColumnasYEnElUltimoBloque() throws Exception {
        byte[] xlsx = builder.generar(List.of(new HojaEtiquetas("HOJA", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // Último bloque: fila base 0-based 73.
            int base = EtiquetasArticuloExcelBuilder.filaBase(
                    EtiquetasArticuloExcelBuilder.BLOQUES - 1);
            assertEquals(73, base);
            for (int izquierda : EtiquetasArticuloExcelBuilder.COLUMNAS_IZQUIERDA) {
                assertEquals("USL738.AL0137",
                        hoja.getRow(base).getCell(izquierda).getStringCellValue());
                assertEquals("Size: U",
                        hoja.getRow(base).getCell(izquierda + 1).getStringCellValue());
                assertEquals("A236 TRUFFLE",
                        hoja.getRow(base + 1).getCell(izquierda).getStringCellValue());
                assertEquals("Cde: 07714",
                        hoja.getRow(base + 1).getCell(izquierda + 1).getStringCellValue());
            }
        }
    }

    @Test
    void cuarentaAnclajesPeroUnaSolaImagenPorHoja() throws Exception {
        byte[] xlsx = builder.generar(List.of(new HojaEtiquetas("HOJA", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            assertEquals(40, EtiquetasArticuloExcelBuilder.ETIQUETAS_POR_HOJA);
            // Los bytes del código de barras se guardan UNA vez, como en los
            // ficheros del cliente: una imagen y 40 anclajes que la reusan.
            assertEquals(1, libro.getAllPictures().size());
            XSSFDrawing dibujo = libro.getSheetAt(0).getDrawingPatriarch();
            assertEquals(40, dibujo.getShapes().size());
        }
    }

    @Test
    void unaEtiquetaSinEan13SeGeneraSinCodigoDeBarras() throws Exception {
        EtiquetaArticulo sinCodigo = new EtiquetaArticulo(
                "USL738.AL0137", "Size: U", "A236 TRUFFLE", "Cde: 07714", null);

        byte[] xlsx = builder.generar(List.of(new HojaEtiquetas("HOJA", sinCodigo)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            assertTrue(libro.getAllPictures().isEmpty());
            // Los textos sí están: la hoja es útil aunque falte el código.
            int base = EtiquetasArticuloExcelBuilder.filaBase(0);
            assertEquals("USL738.AL0137",
                    libro.getSheetAt(0).getRow(base).getCell(0).getStringCellValue());
        }
    }

    @Test
    void elCuerpoDeLaCeldaDeColorSeReduceSegunLaLongitud() throws Exception {
        // 10,5pt hasta 14 caracteres; 9pt de 15 a 17; 8pt a partir de 18.
        assertEquals(210, EtiquetasArticuloExcelBuilder.cuerpoPara("001 BLACK"));
        assertEquals(210, EtiquetasArticuloExcelBuilder.cuerpoPara("2221 CHOCOLATE"));
        assertEquals(180, EtiquetasArticuloExcelBuilder.cuerpoPara("221 DARK COFFEE"));
        assertEquals(180, EtiquetasArticuloExcelBuilder.cuerpoPara("A184 MASTIC BEIGE"));
        // Borde exacto de la regla: 18 caracteres ya caen en el tramo de 160.
        assertEquals(160, EtiquetasArticuloExcelBuilder.cuerpoPara("A328 SAND-CHOCOLAT"));
        assertEquals(160, EtiquetasArticuloExcelBuilder.cuerpoPara("A328 SAND-CHOCOLATE"));

        EtiquetaArticulo corta = new EtiquetaArticulo("REF", "Size: U", "001 BLACK", "Cde: 1", null);
        EtiquetaArticulo larga = new EtiquetaArticulo("REF", "Size: U",
                "A328 SAND-CHOCOLATE", "Cde: 1", null);
        byte[] xlsx = builder.generar(List.of(
                new HojaEtiquetas("CORTA", corta), new HojaEtiquetas("LARGA", larga)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            int base = EtiquetasArticuloExcelBuilder.filaBase(0);
            short cuerpoCorta = libro.getSheetAt(0).getRow(base + 1).getCell(0)
                    .getCellStyle().getFont().getFontHeight();
            short cuerpoLarga = libro.getSheetAt(1).getRow(base + 1).getCell(0)
                    .getCellStyle().getFont().getFontHeight();
            assertEquals(210, cuerpoCorta);
            assertEquals(160, cuerpoLarga);
            assertNotEquals(cuerpoCorta, cuerpoLarga);
        }
    }

    @Test
    void laTallaYElPedidoVanAlineadosALaDerecha() throws Exception {
        byte[] xlsx = builder.generar(List.of(new HojaEtiquetas("HOJA", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            int base = EtiquetasArticuloExcelBuilder.filaBase(0);
            assertEquals(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT,
                    hoja.getRow(base).getCell(1).getCellStyle().getAlignment());
            assertEquals(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT,
                    hoja.getRow(base + 1).getCell(1).getCellStyle().getAlignment());
        }
    }

    @Test
    void dosHojasConElMismoNombreNoRompenLaGeneracion() throws Exception {
        byte[] xlsx = builder.generar(List.of(
                new HojaEtiquetas("MISMO NOMBRE", BOLSO),
                new HojaEtiquetas("MISMO NOMBRE", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            assertEquals(2, libro.getNumberOfSheets());
            assertEquals("MISMO NOMBRE", libro.getSheetName(0));
            assertEquals("MISMO NOMBRE-2", libro.getSheetName(1));
        }
    }

    @Test
    void dosHojasQueSoloDifierenEnMayusculasTampocoRompenLaGeneracion() throws Exception {
        // POI compara los nombres de hoja con equalsIgnoreCase: sin una red
        // insensible a mayúsculas, la segunda hoja hace que lance y se cae el
        // fichero entero.
        byte[] xlsx = builder.generar(List.of(
                new HojaEtiquetas("USL738.AL0137 NOIR 07704", BOLSO),
                new HojaEtiquetas("USL738.AL0137 Noir 07704", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            assertEquals(2, libro.getNumberOfSheets());
        }
    }

    @Test
    void elSufijoDeDesempateRecortaCuandoElNombreYaMideTreintaYUno() {
        java.util.Set<String> usados = new java.util.HashSet<>();
        String largo = "A".repeat(31);
        assertEquals(largo, EtiquetasArticuloExcelBuilder.nombreUnico(usados, largo));
        String segundo = EtiquetasArticuloExcelBuilder.nombreUnico(usados, largo);
        assertEquals(31, segundo.length());
        assertTrue(segundo.endsWith("-2"));
    }

    @Test
    void sinHojasNoSeGeneraNada() {
        // Excel no abre un libro sin hojas: el generador nunca debe llamar
        // al builder con un grupo vacío, y si lo hace se entera.
        assertThrows(IllegalArgumentException.class, () -> builder.generar(List.of()));
    }
}

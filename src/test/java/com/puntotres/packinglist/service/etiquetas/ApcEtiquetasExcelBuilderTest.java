package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.etiquetas.ApcEtiquetasExcelBuilder.EtiquetaCajaApc;
import com.puntotres.packinglist.service.etiquetas.ApcEtiquetasExcelBuilder.EtiquetaPaletApc;

class ApcEtiquetasExcelBuilderTest {

    private final ApcEtiquetasExcelBuilder builder = new ApcEtiquetasExcelBuilder();

    private static EtiquetaCajaApc etiqueta(String colisage, String poids) {
        return new EtiquetaCajaApc("NOT FOUND", "NOT FOUND", "PXCBC-F67008",
                "LZZ-NOIR", "U", "11", colisage, poids);
    }

    @Test
    void escribeElParDeEtiquetasYConservaLasDosHojas() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN,
                List.of(etiqueta("1 / 1", "7,60 Kg")), List.of());
        try (XSSFWorkbook libro = abrir(excel)) {
            assertEquals(2, libro.getNumberOfSheets());
            XSSFSheet cajas = libro.getSheet("Etiquette colis Bolloré ");
            assertNotNull(cajas);
            assertNotNull(libro.getSheet("Etiquette Palette Bolloré"));
            // Etiqueta 1 (los códigos pisan los valores de ejemplo).
            assertEquals("NOT FOUND", texto(cajas, 9, 2));
            assertEquals("NOT FOUND", texto(cajas, 10, 2));
            assertEquals("PXCBC-F67008", texto(cajas, 11, 2));
            assertEquals("LZZ-NOIR", texto(cajas, 12, 2));
            assertEquals("U", texto(cajas, 13, 2));
            assertEquals("11", texto(cajas, 14, 2));
            assertEquals("1 / 1", texto(cajas, 17, 2));
            assertEquals("7,60 Kg", texto(cajas, 18, 2));
            // Etiqueta 2 = mismas celdas + offset 21.
            assertEquals("NOT FOUND", texto(cajas, 9 + 21, 2));
            assertEquals("PXCBC-F67008", texto(cajas, 11 + 21, 2));
            assertEquals("7,60 Kg", texto(cajas, 18 + 21, 2));
            // Los estáticos de la plantilla no se tocan (DESTINATION = TOKYO).
            assertEquals("TOKYO", texto(cajas, 8, 2));
        }
    }

    @Test
    void replicaElBloquePorCajaConSaltoDePaginaYPesoEnBlancoSiFalta() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN, List.of(
                etiqueta("1 / 2", "7,60 Kg"), etiqueta("2 / 2", null)), List.of());
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet cajas = libro.getSheet("Etiquette colis Bolloré ");
            // Caja 2 = bloque desplazado 42 filas, con sus estáticos copiados.
            assertEquals("NOT FOUND", texto(cajas, 9 + 42, 2));
            assertEquals("2 / 2", texto(cajas, 17 + 42, 2));
            assertEquals("", texto(cajas, 18 + 42, 2));
            assertEquals("TOKYO", texto(cajas, 8 + 42, 2));
            // Cada par en su A4.
            assertTrue(cajas.getRowBreaks().length >= 1);
            assertEquals(41, cajas.getRowBreaks()[0]);
            // El bloque copiado conserva los altos de fila de la plantilla.
            assertEquals(cajas.getRow(6).getHeightInPoints(),
                    cajas.getRow(6 + 42).getHeightInPoints(), 0.01);
        }
    }

    @Test
    void replicaElLogoDeLaPlantillaEnCadaBloque() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN, List.of(
                etiqueta("1 / 2", "7,60 Kg"), etiqueta("2 / 2", "8,20 Kg")), List.of());
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet cajas = libro.getSheet("Etiquette colis Bolloré ");
            // La plantilla trae 2 logos (uno por etiqueta del par): con 2
            // cajas debe haber 4.
            assertEquals(4, cajas.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void generaUnaEtiquetaDePaletPorPaletConSaltoDePagina() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN,
                List.of(etiqueta("1 / 1", "7,60 Kg")),
                List.of(new EtiquetaPaletApc(9, "64,58 Kg"), new EtiquetaPaletApc(3, null)));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet palet = libro.getSheet("Etiquette Palette Bolloré");
            // Palet 1: nº de cajas numérico en C13 y peso en C14.
            assertEquals(9, palet.getRow(12).getCell(2).getNumericCellValue(), 0.001);
            assertEquals("64,58 Kg", texto(palet, 13, 2));
            // Palet 2 = bloque desplazado 14 filas; peso null en blanco.
            assertEquals(3, palet.getRow(12 + 14).getCell(2).getNumericCellValue(), 0.001);
            assertEquals("", texto(palet, 13 + 14, 2));
            // Estáticos copiados y salto de página entre palets.
            assertEquals("TOKYO", texto(palet, 9 + 14, 2));
            assertTrue(palet.getRowBreaks().length >= 1);
            assertEquals(13, palet.getRowBreaks()[0]);
            // La celda suelta del contador manual de la fila 1 se limpia
            // (en la plantilla de JAPAN es E1).
            assertEquals("", texto(palet, 0, 4));
        }
    }

    @Test
    void sinPaletsLaHojaDePaletQuedaConLosValoresEnBlanco() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN,
                List.of(etiqueta("1 / 1", "7,60 Kg")), List.of());
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet palet = libro.getSheet("Etiquette Palette Bolloré");
            assertEquals("", texto(palet, 12, 2));
            assertEquals("", texto(palet, 13, 2));
        }
        // Copia para inspección manual, como hace el e2e de packing lists.
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
        java.nio.file.Files.write(
                java.nio.file.Path.of("target", "etiquetas-apc-japan.xlsx"), excel);
    }

    static XSSFWorkbook abrir(byte[] contenido) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(contenido));
    }

    static String texto(XSSFSheet hoja, int fila, int col) {
        if (hoja.getRow(fila) == null || hoja.getRow(fila).getCell(col) == null) {
            return "";
        }
        return hoja.getRow(fila).getCell(col).toString().trim();
    }
}

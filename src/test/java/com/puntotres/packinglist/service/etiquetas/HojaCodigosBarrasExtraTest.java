package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class HojaCodigosBarrasExtraTest {

    private static FilaCodigoBarrasExtra fila(String referencia, String ean13) {
        return new FilaCodigoBarrasExtra("3 / 15", "CHINA",
                new EtiquetaArticulo(referencia, "Size: U", "221 DARK COFFEE",
                        "Cde: 07703", ean13),
                "366659835477100001000077030000000000000000ES");
    }

    @Test
    void sinFilasNoSeCreaLaHoja() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            libro.createSheet("AMI CHINA");
            HojaCodigosBarrasExtra.escribir(libro, List.of());
            assertEquals(1, libro.getNumberOfSheets());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void cadaArticuloOcupaUnBloqueConSusTresColumnas() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            HojaCodigosBarrasExtra.escribir(libro,
                    List.of(fila("ULL163.AL0052", "3666598354771")));
            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            int base = RejillaEtiquetas.filaBase(0);
            assertEquals("CAJA", hoja.getRow(base).getCell(0).getStringCellValue());
            assertEquals("3 / 15", hoja.getRow(base).getCell(1).getStringCellValue());
            assertEquals("Destinación",
                    hoja.getRow(base + 2).getCell(0).getStringCellValue());
            assertEquals("CHINA", hoja.getRow(base + 2).getCell(1).getStringCellValue());
            // Los mismos cuatro textos en el bloque del EAN13 y en el del EAN128.
            for (int columna : new int[] {3, 6}) {
                assertEquals("ULL163.AL0052",
                        hoja.getRow(base).getCell(columna).getStringCellValue());
                assertEquals("Size: U",
                        hoja.getRow(base).getCell(columna + 1).getStringCellValue());
                assertEquals("221 DARK COFFEE",
                        hoja.getRow(base + 1).getCell(columna).getStringCellValue());
                assertEquals("Cde: 07703",
                        hoja.getRow(base + 1).getCell(columna + 1).getStringCellValue());
            }
            assertNull(hoja.getRow(base).getCell(9), "la cuarta columna va vacía");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void cadaArticuloLlevaSusDosImagenes() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            HojaCodigosBarrasExtra.escribir(libro, List.of(
                    fila("ULL163.AL0052", "3666598354771"),
                    fila("ULL745.AL0103", "3666598354771")));
            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals(4, hoja.getDrawingPatriarch().getShapes().size());
            // El mismo código en dos artículos se guarda una sola vez.
            assertEquals(2, libro.getAllPictures().size());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void unArticuloSinEan13SaleIgualPeroSinEsaImagen() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            HojaCodigosBarrasExtra.escribir(libro, List.of(fila("ULL163.AL0052", null)));
            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals("ULL163.AL0052",
                    hoja.getRow(RejillaEtiquetas.filaBase(0)).getCell(3).getStringCellValue());
            assertEquals(1, hoja.getDrawingPatriarch().getShapes().size());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void onceArticulosOcupanDosPaginas() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            HojaCodigosBarrasExtra.escribir(libro, java.util.stream.IntStream.range(0, 11)
                    .mapToObj(i -> fila("REF" + i, "3666598354771")).toList());
            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals(1, hoja.getRowBreaks().length);
            assertEquals("REF10",
                    hoja.getRow(RejillaEtiquetas.filaBase(10)).getCell(3).getStringCellValue());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void laHojaSaleEnVerticalYAEscalaDeImpresion() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            HojaCodigosBarrasExtra.escribir(libro,
                    List.of(fila("ULL163.AL0052", "3666598354771")));
            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertTrue(!hoja.getPrintSetup().getLandscape());
            assertEquals(74, hoja.getPrintSetup().getScale());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

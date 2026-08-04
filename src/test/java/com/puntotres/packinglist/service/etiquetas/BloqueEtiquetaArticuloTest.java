package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class BloqueEtiquetaArticuloTest {

    private static final EtiquetaArticulo ETIQUETA = new EtiquetaArticulo(
            "ULL163.AL0052", "Size: U", "221 DARK COFFEE", "Cde: 07703", "3666598354771");

    @Test
    void escribeLosCuatroTextosEnSusCeldas() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = RejillaEtiquetas.crearHojaMaquetada(libro, "H", 1);
            new BloqueEtiquetaArticulo(libro).escribirTextos(hoja, 1, 3, ETIQUETA);
            assertEquals("ULL163.AL0052", hoja.getRow(1).getCell(3).getStringCellValue());
            assertEquals("Size: U", hoja.getRow(1).getCell(4).getStringCellValue());
            assertEquals("221 DARK COFFEE", hoja.getRow(2).getCell(3).getStringCellValue());
            assertEquals("Cde: 07703", hoja.getRow(2).getCell(4).getStringCellValue());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void reutilizaElEstiloEntreBloquesDelMismoLibro() {
        // POI acumula los estilos por libro y un fichero de cinturones tiene
        // 79 hojas × 40 etiquetas: sin caché se dispara el tope de ~64.000.
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = RejillaEtiquetas.crearHojaMaquetada(libro, "H", 2);
            BloqueEtiquetaArticulo bloques = new BloqueEtiquetaArticulo(libro);
            bloques.escribirTextos(hoja, 1, 0, ETIQUETA);
            bloques.escribirTextos(hoja, 9, 0, ETIQUETA);
            assertEquals(hoja.getRow(2).getCell(0).getCellStyle().getIndex(),
                    hoja.getRow(10).getCell(0).getCellStyle().getIndex());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void laRejillaDeUnaPaginaSaleIgualQueLaDeSiempre() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = RejillaEtiquetas.crearHojaMaquetada(
                    libro, "H", RejillaEtiquetas.BLOQUES_POR_PAGINA);
            // La última separadora no se crea: si se creara, la hoja sería
            // 9,95 pt más alta y saldría una segunda página al imprimir.
            assertNull(hoja.getRow(RejillaEtiquetas.filaBase(9)
                    + RejillaEtiquetas.FILAS_POR_BLOQUE - 1));
            assertEquals(0, hoja.getRowBreaks().length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void conMasDeUnaPaginaHaySaltoAlFinalDeCadaUna() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = RejillaEtiquetas.crearHojaMaquetada(libro, "H", 12);
            assertEquals(1, hoja.getRowBreaks().length);
            assertEquals(RejillaEtiquetas.filaBase(9)
                    + RejillaEtiquetas.FILAS_POR_BLOQUE - 1, hoja.getRowBreaks()[0]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

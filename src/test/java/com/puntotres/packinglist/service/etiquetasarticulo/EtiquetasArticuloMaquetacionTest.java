package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.etiquetas.EtiquetaArticulo;

/**
 * Compara la maquetación generada contra la del fichero REAL del cliente.
 *
 * Sin plantilla .xlsx, las medidas viven como constantes en el builder: este
 * test es lo que impide que deriven. Si alguien cambia un ancho, un alto o
 * el pageSetup, cae aquí señalando el fichero del cliente como verdad.
 */
class EtiquetasArticuloMaquetacionTest {

    private static final Path FICHERO_REAL =
            Path.of("docs/Etiquetas para etiquetas/AMI CODE BARRE H26 MOROCCO.xlsx");

    @Test
    void laMaquetacionCoincideConElFicheroRealDelCliente() throws Exception {
        assertTrue(Files.exists(FICHERO_REAL),
                "falta el fichero de referencia del cliente: " + FICHERO_REAL);

        byte[] generado = new EtiquetasArticuloExcelBuilder().generar(List.of(
                new HojaEtiquetas("HOJA", new EtiquetaArticulo(
                        "USL738.AL0137", "Size: U", "A236 TRUFFLE", "Cde: 07714",
                        "3666598543892"))));

        try (InputStream real = Files.newInputStream(FICHERO_REAL);
             XSSFWorkbook libroReal = new XSSFWorkbook(real);
             XSSFWorkbook libroGenerado = new XSSFWorkbook(new ByteArrayInputStream(generado))) {

            XSSFSheet esperada = libroReal.getSheetAt(0);
            XSSFSheet obtenida = libroGenerado.getSheetAt(0);

            for (int columna = 0; columna < 11; columna++) {
                assertEquals(esperada.getColumnWidth(columna), obtenida.getColumnWidth(columna),
                        "ancho de la columna " + columna);
            }

            assertEquals(esperada.getRow(0).getHeightInPoints(),
                    obtenida.getRow(0).getHeightInPoints(), 0.001f, "alto de la fila 0");
            assertEquals(esperada.getDefaultRowHeightInPoints(),
                    obtenida.getDefaultRowHeightInPoints(), 0.001f, "alto por defecto");

            // getLastRowNum() es la aserción que habría cazado la fila de
            // más (el índice 80: la separadora del último bloque) que colaba
            // una segunda página en la impresión.
            assertEquals(esperada.getLastRowNum(), obtenida.getLastRowNum(), "última fila usada");

            // Filas separadoras de los bloques 0 a 8, sin excepciones: las 9
            // deben existir y coincidir en alto en los dos ficheros.
            for (int bloque = 0; bloque < EtiquetasArticuloExcelBuilder.BLOQUES - 1; bloque++) {
                int separadora = EtiquetasArticuloExcelBuilder.filaBase(bloque)
                        + EtiquetasArticuloExcelBuilder.FILAS_POR_BLOQUE - 1;
                assertEquals(esperada.getRow(separadora).getHeightInPoints(),
                        obtenida.getRow(separadora).getHeightInPoints(), 0.001f,
                        "alto de la fila separadora " + separadora);
            }

            // El bloque 9 (el último) NO lleva separadora: se comprueba
            // explícitamente en los dos ficheros, no con un bucle que se
            // salta el caso y lo esconde.
            int separadoraUltimoBloque = EtiquetasArticuloExcelBuilder.filaBase(
                    EtiquetasArticuloExcelBuilder.BLOQUES - 1)
                    + EtiquetasArticuloExcelBuilder.FILAS_POR_BLOQUE - 1;
            assertNull(esperada.getRow(separadoraUltimoBloque),
                    "el fichero real no debe tener la fila " + separadoraUltimoBloque);
            assertNull(obtenida.getRow(separadoraUltimoBloque),
                    "lo generado no debe tener la fila " + separadoraUltimoBloque);

            // Estructura completa: para cada índice hasta la última fila
            // usada, la fila existe en los dos ficheros con el mismo alto, o
            // no existe en ninguno. Si existiera en uno y no en el otro, o
            // con un alto distinto, falla aquí.
            int ultimaFila = Math.max(esperada.getLastRowNum(), obtenida.getLastRowNum());
            for (int i = 0; i <= ultimaFila; i++) {
                Row filaEsperada = esperada.getRow(i);
                Row filaObtenida = obtenida.getRow(i);
                if (filaEsperada == null || filaObtenida == null) {
                    assertEquals(filaEsperada == null, filaObtenida == null,
                            "presencia de la fila " + i);
                } else {
                    assertEquals(filaEsperada.getHeightInPoints(),
                            filaObtenida.getHeightInPoints(), 0.001f, "alto de la fila " + i);
                }
            }

            assertEquals(esperada.getPrintSetup().getScale(),
                    obtenida.getPrintSetup().getScale(), "escala de impresión");
            assertEquals(esperada.getPrintSetup().getPaperSize(),
                    obtenida.getPrintSetup().getPaperSize(), "tamaño de papel");
            assertEquals(esperada.getPrintSetup().getLandscape(),
                    obtenida.getPrintSetup().getLandscape(), "orientación");

            for (PageMargin margen : new PageMargin[] {
                    PageMargin.LEFT, PageMargin.RIGHT, PageMargin.TOP, PageMargin.BOTTOM,
                    PageMargin.HEADER, PageMargin.FOOTER}) {
                assertEquals(esperada.getMargin(margen), obtenida.getMargin(margen), 1e-9,
                        "margen " + margen);
            }
        }

        // Para que Jordi confirme a mano en Vista previa de impresión que
        // sale UNA página, no dos: es lo único que ningún test puede ver.
        Path volcado = Path.of("target/etiquetas-articulo-maquetacion.xlsx");
        Files.createDirectories(volcado.getParent());
        Files.write(volcado, generado);
    }

    @Test
    void losValoresEsperadosSonLosDelSpike() throws Exception {
        // Duplicado deliberado: si el fichero de docs/ desaparece o cambia,
        // estos valores siguen documentando qué maquetación se espera.
        byte[] generado = new EtiquetasArticuloExcelBuilder().generar(List.of(
                new HojaEtiquetas("HOJA", new EtiquetaArticulo(
                        "REF", "Size: U", "001 BLACK", "Cde: 1", null))));

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(generado))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            int[] anchos = {3766, 4425, 621, 3766, 4534, 512, 3766, 4534, 621, 3766, 4534};
            for (int columna = 0; columna < anchos.length; columna++) {
                assertEquals(anchos[columna], hoja.getColumnWidth(columna),
                        "ancho de la columna " + columna);
            }
            assertEquals(6.0f, hoja.getRow(0).getHeightInPoints(), 0.001f);
            assertEquals(9.95f, hoja.getRow(8).getHeightInPoints(), 0.001f);
            assertEquals(15.0f, hoja.getDefaultRowHeightInPoints(), 0.001f);
            // Última fila usada: 74. El bloque 9 (el último) no lleva
            // separadora (esa sería la 80) porque el fichero real tampoco
            // la tiene: crearla añadiría una página en blanco al imprimir.
            assertEquals(74, hoja.getLastRowNum());
            assertEquals(74, hoja.getPrintSetup().getScale());
            assertEquals(9, hoja.getPrintSetup().getPaperSize());
            assertEquals(false, hoja.getPrintSetup().getLandscape());
            assertEquals(0.0, hoja.getMargin(PageMargin.LEFT), 1e-9);
            assertEquals(0.03937007874015748, hoja.getMargin(PageMargin.RIGHT), 1e-9);
            assertEquals(0.03937007874015748, hoja.getMargin(PageMargin.TOP), 1e-9);
            assertEquals(0.03937007874015748, hoja.getMargin(PageMargin.BOTTOM), 1e-9);
            assertEquals(0.31496062992125984, hoja.getMargin(PageMargin.HEADER), 1e-9);
            assertEquals(0.31496062992125984, hoja.getMargin(PageMargin.FOOTER), 1e-9);
        }
    }
}

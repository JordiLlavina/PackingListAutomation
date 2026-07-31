package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class AjusteFuenteTest {

    // --- el cálculo, sin POI ---

    @Test
    void unTextoQueCabeConservaSuTamano() {
        // 10 chars de ancho a 11 pt = capacidad 10; el texto son 10.
        assertEquals((short) 11, AjusteFuente.tamano("1234567890", 10.0, (short) 11));
    }

    @Test
    void unTextoQueNoCabeEncogeProporcionalmente() {
        // Ancho 20 chars, fuente 22 pt -> capacidad 10 chars; texto de 15
        // -> 22 * 10 / 15 = 14,66 -> 14.
        assertEquals((short) 14, AjusteFuente.tamano("123456789012345", 20.0, (short) 22));
    }

    @Test
    void nuncaBajaDelMinimoLegible() {
        // Capacidad 10, texto de 40 -> 2,75 pt, que no lo lee nadie.
        assertEquals((short) AjusteFuente.TAMANO_MINIMO_PT,
                AjusteFuente.tamano("1".repeat(40), 10.0, (short) 11));
    }

    @Test
    void elTextoVacioConservaSuTamano() {
        assertEquals((short) 18, AjusteFuente.tamano("", 10.0, (short) 18));
        assertEquals((short) 18, AjusteFuente.tamano(null, 10.0, (short) 18));
    }

    // --- la aplicación sobre la celda ---

    @Test
    void unaCeldaQueCabeNoCambiaDeEstilo() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Cell celda = celdaCon(libro, "CORTO", (short) 11, 30);
            // POI devuelve un XSSFCellStyle nuevo en cada getCellStyle(): "el mismo
            // estilo" solo se puede comprobar por getIndex().
            short indiceAntes = celda.getCellStyle().getIndex();
            int estilosAntes = libro.getNumCellStyles();

            new AjusteFuente(libro).ajustar(celda);

            // Ni estilo nuevo, ni shrinkToFit: una caja de un solo artículo
            // tiene que producir el mismo fichero que antes de esta clase.
            assertEquals(indiceAntes, celda.getCellStyle().getIndex());
            assertEquals(estilosAntes, libro.getNumCellStyles());
            assertFalse(((XSSFCellStyle) celda.getCellStyle()).getShrinkToFit());
            assertEquals((short) 11,
                    ((XSSFCellStyle) celda.getCellStyle()).getFont().getFontHeightInPoints());
        }
    }

    @Test
    void unaCeldaQueNoCabeRecibeUnEstiloConLaFuenteMasPequenaYShrinkToFit() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Cell celda = celdaCon(libro, "X".repeat(60), (short) 22, 20);

            new AjusteFuente(libro).ajustar(celda);

            XSSFCellStyle estilo = (XSSFCellStyle) celda.getCellStyle();
            assertTrue(estilo.getFont().getFontHeightInPoints() < 22);
            assertTrue(estilo.getShrinkToFit());
        }
    }

    @Test
    void elEstiloClonadoConservaNegritaYNombreDeFuente() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Cell celda = celdaCon(libro, "X".repeat(60), (short) 22, 20);
            XSSFFont original = ((XSSFCellStyle) celda.getCellStyle()).getFont();
            original.setBold(true);
            original.setFontName("Arial");

            new AjusteFuente(libro).ajustar(celda);

            XSSFFont fuente = ((XSSFCellStyle) celda.getCellStyle()).getFont();
            assertTrue(fuente.getBold());
            assertEquals("Arial", fuente.getFontName());
        }
    }

    @Test
    void dosCeldasIgualesComparteEstilo() throws IOException {
        // POI tiene un tope de ~64.000 estilos por libro y estas celdas se
        // escriben dos veces por caja: sin caché un envío grande lo revienta.
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            AjusteFuente ajuste = new AjusteFuente(libro);
            Cell una = celdaCon(libro, "X".repeat(60), (short) 22, 20);
            Cell otra = una.getRow().getSheet().createRow(1).createCell(0);
            otra.setCellStyle(una.getSheet().getRow(0).getCell(0).getCellStyle());
            otra.setCellValue("X".repeat(60));

            int estilosAntes = libro.getNumCellStyles();
            ajuste.ajustar(una);
            ajuste.ajustar(otra);

            assertEquals(estilosAntes + 1, libro.getNumCellStyles());
            // POI devuelve un XSSFCellStyle nuevo en cada getCellStyle(): "el mismo
            // estilo" solo se puede comprobar por getIndex().
            assertEquals(una.getCellStyle().getIndex(), otra.getCellStyle().getIndex());
        }
    }

    @Test
    void unaFuenteYaEnElSueloQueNoCabeRecibeShrinkToFitSinBajarMas() throws IOException {
        // "Cabe" y "se puede bajar más" son preguntas independientes: la
        // fuente ya está en el suelo (8pt) y no hay margen para bajarla,
        // pero el texto tampoco cabe -> shrinkToFit tiene que saltar igual,
        // si no la celda se queda desbordada en silencio.
        // Ancho 5 chars a 8pt -> capacidad 5*11/8 = 6,875; el texto son 10.
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Cell celda = celdaCon(libro, "X".repeat(10), (short) 8, 5);

            new AjusteFuente(libro).ajustar(celda);

            XSSFCellStyle estilo = (XSSFCellStyle) celda.getCellStyle();
            assertEquals((short) AjusteFuente.TAMANO_MINIMO_PT,
                    estilo.getFont().getFontHeightInPoints());
            assertTrue(estilo.getShrinkToFit());
        }
    }

    @Test
    void unaCeldaConWrapTextQueNoCabeEncogeLaFuenteSinMarcarShrinkToFit() throws IOException {
        // Las plantillas de APC llevan wrapText en las celdas de valor:
        // Excel lo prioriza sobre shrinkToFit e ignora este último, así que
        // marcarlo ahí sería un atributo inerte. El tamaño calculado sigue
        // aplicándose igual: es la única protección real en ese caso.
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Cell celda = celdaConWrapText(libro, "X".repeat(60), (short) 22, 20);

            new AjusteFuente(libro).ajustar(celda);

            XSSFCellStyle estilo = (XSSFCellStyle) celda.getCellStyle();
            assertTrue(estilo.getFont().getFontHeightInPoints() < 22);
            assertFalse(estilo.getShrinkToFit());
        }
    }

    @Test
    void dosCeldasEnElSueloQueNoCabenComparteEstilo() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            AjusteFuente ajuste = new AjusteFuente(libro);
            Cell una = celdaCon(libro, "X".repeat(10), (short) 8, 5);
            Cell otra = una.getRow().getSheet().createRow(1).createCell(0);
            otra.setCellStyle(una.getSheet().getRow(0).getCell(0).getCellStyle());
            otra.setCellValue("X".repeat(10));

            int estilosAntes = libro.getNumCellStyles();
            ajuste.ajustar(una);
            ajuste.ajustar(otra);

            assertEquals(estilosAntes + 1, libro.getNumCellStyles());
            assertEquals(una.getCellStyle().getIndex(), otra.getCellStyle().getIndex());
        }
    }

    /** Una celda con texto, tamaño de fuente y ancho de columna dados. */
    private static Cell celdaCon(XSSFWorkbook libro, String texto, short tamanoPt,
                                 int anchoEnChars) {
        return celdaCon(libro, texto, tamanoPt, anchoEnChars, false);
    }

    /** Igual que {@link #celdaCon}, pero con wrapText en el estilo original. */
    private static Cell celdaConWrapText(XSSFWorkbook libro, String texto, short tamanoPt,
                                 int anchoEnChars) {
        return celdaCon(libro, texto, tamanoPt, anchoEnChars, true);
    }

    private static Cell celdaCon(XSSFWorkbook libro, String texto, short tamanoPt,
                                 int anchoEnChars, boolean wrapText) {
        XSSFSheet hoja = libro.createSheet("h" + libro.getNumberOfSheets());
        hoja.setColumnWidth(0, anchoEnChars * 256);
        XSSFFont fuente = libro.createFont();
        fuente.setFontHeightInPoints(tamanoPt);
        XSSFCellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente);
        estilo.setWrapText(wrapText);
        Cell celda = hoja.createRow(0).createCell(0);
        celda.setCellValue(texto);
        celda.setCellStyle(estilo);
        return celda;
    }
}

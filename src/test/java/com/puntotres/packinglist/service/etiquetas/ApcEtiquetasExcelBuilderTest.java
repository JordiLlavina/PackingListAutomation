package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                "LZZ-NOIR", "U", "11", colisage, poids, null);
    }

    @Test
    void escribeElParDeEtiquetasYConservaLasDosHojas() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN,
                List.of(etiqueta("1 / 1", "7,60 Kg")),
                List.of(new EtiquetaPaletApc(1, 1, "17,60 Kg", null)));
        try (XSSFWorkbook libro = abrir(excel)) {
            assertEquals(2, libro.getNumberOfSheets());
            XSSFSheet cajas = libro.getSheet("Etiquette colis Bolloré ");
            assertNotNull(cajas);
            assertNotNull(libro.getSheet("Etiquette Palette Bolloré"));
            ApcEtiquetaLayout j = ApcEtiquetaLayout.JAPAN;
            int offset = j.offsetSegundaEtiqueta();
            // Etiqueta 1 (los códigos pisan los valores de ejemplo).
            assertEquals("NOT FOUND", texto(cajas, j.filaOrder(), 2));
            assertEquals("NOT FOUND", texto(cajas, j.filaLivraison(), 2));
            assertEquals("PXCBC-F67008", texto(cajas, j.filaReferencia(), 2));
            assertEquals("LZZ-NOIR", texto(cajas, j.filaColor(), 2));
            assertEquals("U", texto(cajas, j.filaTalla(), 2));
            assertEquals("11", texto(cajas, j.filaPiezas(), 2));
            assertEquals("1 / 1", texto(cajas, j.filaColisage(), 2));
            assertEquals("7,60 Kg", texto(cajas, j.filaPeso(), 2));
            // Etiqueta 2 = mismas celdas + offset.
            assertEquals("NOT FOUND", texto(cajas, j.filaOrder() + offset, 2));
            assertEquals("PXCBC-F67008", texto(cajas, j.filaReferencia() + offset, 2));
            assertEquals("7,60 Kg", texto(cajas, j.filaPeso() + offset, 2));
            // Los estáticos de la plantilla no se tocan: DESTINATION = TOKYO,
            // la fila justo encima del Order N°.
            assertEquals("TOKYO", texto(cajas, j.filaOrder() - 1, 2));
        }
    }

    @Test
    void replicaElBloquePorCajaConSaltoDePaginaYPesoEnBlancoSiFalta() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN, List.of(
                etiqueta("1 / 2", "7,60 Kg"), etiqueta("2 / 2", null)), List.of());
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet cajas = libro.getSheet("Etiquette colis Bolloré ");
            ApcEtiquetaLayout j = ApcEtiquetaLayout.JAPAN;
            int bloque = j.alturaBloque();
            // Caja 2 = bloque desplazado, con sus estáticos copiados.
            assertEquals("NOT FOUND", texto(cajas, j.filaOrder() + bloque, 2));
            assertEquals("2 / 2", texto(cajas, j.filaColisage() + bloque, 2));
            assertEquals("", texto(cajas, j.filaPeso() + bloque, 2));
            assertEquals("TOKYO", texto(cajas, j.filaOrder() - 1 + bloque, 2));
            // Cada par en su A4.
            assertTrue(cajas.getRowBreaks().length >= 1);
            assertEquals(bloque - 1, cajas.getRowBreaks()[0]);
            // El bloque copiado conserva los altos de fila de la plantilla.
            assertEquals(cajas.getRow(6).getHeightInPoints(),
                    cajas.getRow(6 + bloque).getHeightInPoints(), 0.01);
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

    /**
     * La línea DESTINATION solo cambia cuando la etiqueta trae destino (caja
     * con varias destinaciones hijas) y la plantilla la rotula; en las demás
     * plantillas es un aeropuerto y se queda como está aunque llegue uno.
     */
    @Test
    void laDestinacionSoloSeReescribeDondeLaPlantillaNombraLaDestinacion() throws IOException {
        EtiquetaCajaApc mixta = new EtiquetaCajaApc("4100128710 / 4100128799", "PUN1",
                "PXCBC-F67008 / PXCBC-F67008", "LZZ-NOIR / LZZ-NOIR", "U", "6 / 4",
                "1 / 1", "7,60 Kg", "WHOLESALE / AUSTRALIA");
        ApcEtiquetaLayout wh = ApcEtiquetaLayout.WH_CROSSLOG;
        try (XSSFWorkbook libro = abrir(builder.generar(wh, List.of(mixta),
                List.of(new EtiquetaPaletApc(1, 1, "17,60 Kg", null))))) {
            assertEquals("WHOLESALE / AUSTRALIA",
                    texto(libro.getSheet(wh.hojaCajas()), wh.filaDestino(), wh.colValor()));
        }
        try (XSSFWorkbook libro = abrir(builder.generar(wh, List.of(etiqueta("1 / 1", "7,60 Kg")),
                List.of(new EtiquetaPaletApc(1, 1, "17,60 Kg", null))))) {
            assertEquals("WHOLESALE",
                    texto(libro.getSheet(wh.hojaCajas()), wh.filaDestino(), wh.colValor()));
        }
        try (XSSFWorkbook libro = abrir(builder.generar(ApcEtiquetaLayout.JAPAN, List.of(mixta),
                List.of(new EtiquetaPaletApc(1, 1, "17,60 Kg", null))))) {
            // JAPAN: la fila 8 es DESTINATION, y dice TOKYO pase lo que pase.
            assertEquals("TOKYO", texto(libro.getSheet(ApcEtiquetaLayout.JAPAN.hojaCajas()), 7, 2));
        }
    }

    @Test
    void generaUnaEtiquetaDePaletPorPaletConSaltoDePagina() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN,
                List.of(etiqueta("1 / 1", "7,60 Kg")),
                List.of(new EtiquetaPaletApc(1, 9, "64,58 Kg", null), new EtiquetaPaletApc(2, 3, null, null)));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet palet = libro.getSheet("Etiquette Palette Bolloré");
            ApcEtiquetaLayout.Palet geo = ApcEtiquetaLayout.JAPAN.palet();
            // Palet 1: nº de cajas numérico y peso debajo.
            assertEquals(9, palet.getRow(geo.filaNumCajas()).getCell(geo.colValor())
                    .getNumericCellValue(), 0.001);
            assertEquals("64,58 Kg", texto(palet, geo.filaPeso(), geo.colValor()));
            // Palet 2 = bloque desplazado; peso null en blanco.
            assertEquals(3, palet.getRow(geo.filaNumCajas() + geo.altura()).getCell(geo.colValor())
                    .getNumericCellValue(), 0.001);
            assertEquals("", texto(palet, geo.filaPeso() + geo.altura(), geo.colValor()));
            // Estáticos copiados y salto de página entre palets.
            assertEquals("TOKYO", texto(palet, 7 + geo.altura(), geo.colValor()));
            assertTrue(palet.getRowBreaks().length >= 1);
            assertEquals(geo.altura() - 1, palet.getRowBreaks()[0]);
            // La celda suelta del contador manual de la fila 1 se limpia
            // (en la plantilla de JAPAN es E1).
            assertEquals("", texto(palet, 0, 4));
        }
        // Copia para inspección manual, como hace el e2e de packing lists.
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
        java.nio.file.Files.write(
                java.nio.file.Path.of("target", "etiquetas-apc-japan.xlsx"), excel);
    }

    @Test
    void sinPaletsElExcelSaleSinLaHojaDePalet() throws IOException {
        // Una etiqueta de palet en blanco se imprime y se pega en un bulto
        // igual que una buena, así que la hoja se quita entera. Es el caso de
        // la destinación que va suelta, con sus cajas en el palet 0.
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN,
                List.of(etiqueta("1 / 1", "7,60 Kg")), List.of());
        try (XSSFWorkbook libro = abrir(excel)) {
            assertEquals(1, libro.getNumberOfSheets());
            assertNull(libro.getSheet("Etiquette Palette Bolloré"));
            // Y la que queda, seleccionada: si no, Excel abre el libro sin
            // ninguna pestaña activa.
            assertTrue(libro.getSheetAt(0).isSelected());
        }
    }

    /**
     * El área de impresión de la plantilla cubre su único bloque: sin
     * estirarla, un envío de varias cajas imprimiría solo la primera. Se
     * estira hasta la última fila escrita CONSERVANDO las columnas que eligió
     * el cliente, que no son las mismas en todas las hojas (en la de palet de
     * WHOLESALE llega hasta la B, porque ahí no existe la columna A).
     */
    @Test
    void elAreaDeImpresionCubreTodosLosBloquesYRespetaLasColumnasDeCadaPlantilla()
            throws IOException {
        for (ApcEtiquetaLayout layout : List.of(ApcEtiquetaLayout.JAPAN, ApcEtiquetaLayout.KOREA,
                ApcEtiquetaLayout.USA, ApcEtiquetaLayout.WH_CROSSLOG, ApcEtiquetaLayout.RETAIL)) {
            byte[] excel = builder.generar(layout,
                    List.of(etiqueta("1 / 3", "7,60 Kg"), etiqueta("2 / 3", "7,60 Kg"),
                            etiqueta("3 / 3", "6,20 Kg")),
                    List.of(new EtiquetaPaletApc(1, 2, "25,20 Kg", null),
                            new EtiquetaPaletApc(2, 1, "16,20 Kg", null)));
            try (XSSFWorkbook libro = abrir(excel)) {
                String donde = layout.rutaPlantilla() + ": ";
                assertEquals(3 * layout.alturaBloque() - 1,
                        ultimaFilaDelArea(libro, 0), donde + "hoja de cajas");
                assertEquals(2 * layout.palet().altura() - 1,
                        ultimaFilaDelArea(libro, 1), donde + "hoja de palet");
                // Y sobre todo: la columna donde se escribe el valor cae
                // DENTRO del área. En la hoja de palet de WHOLESALE el área
                // acaba en la B, así que escribir en la C (como las otras
                // cuatro) dejaría el número de cajas y el peso sin imprimir.
                assertTrue(ultimaColumnaDelArea(libro, 1) >= layout.palet().colValor(),
                        donde + "el valor del palet cae fuera del área de impresión");
            }
        }
    }

    /**
     * Las plantillas vienen con "ajustar todas las filas en una página", que
     * sobre su único bloque es justo lo que se quiere pero sobre N le pediría
     * a Excel meter TODAS las etiquetas en un solo A4. Se traduce a la escala
     * fija que hace que quepa UN bloque, así que el resultado no depende de
     * cuántas cajas lleve el envío.
     */
    @Test
    void cadaBloqueOcupaSuPaginaSeaUnaCajaOVeinte() throws IOException {
        for (ApcEtiquetaLayout layout : List.of(ApcEtiquetaLayout.JAPAN, ApcEtiquetaLayout.KOREA,
                ApcEtiquetaLayout.USA, ApcEtiquetaLayout.WH_CROSSLOG, ApcEtiquetaLayout.RETAIL)) {
            short escalaDeUna = escalaDeLaHojaDeCajas(layout, 1);
            short escalaDeVeinte = escalaDeLaHojaDeCajas(layout, 20);
            String donde = layout.rutaPlantilla() + ": ";
            assertEquals(escalaDeUna, escalaDeVeinte, donde + "la escala depende del nº de cajas");
            assertTrue(escalaDeUna > 0 && escalaDeUna <= 100,
                    donde + "escala fuera de rango: " + escalaDeUna);
        }
    }

    private short escalaDeLaHojaDeCajas(ApcEtiquetaLayout layout, int numCajas) throws IOException {
        List<EtiquetaCajaApc> cajas = new java.util.ArrayList<>();
        for (int i = 1; i <= numCajas; i++) {
            cajas.add(etiqueta(i + " / " + numCajas, "7,60 Kg"));
        }
        try (XSSFWorkbook libro = abrir(builder.generar(layout, cajas,
                List.of(new EtiquetaPaletApc(1, 1, "17,60 Kg", null))))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            assertTrue(!hoja.getFitToPage(),
                    layout.rutaPlantilla() + ": sigue con 'ajustar a una página', que con varias"
                            + " cajas mete todas las etiquetas en un solo A4");
            return hoja.getPrintSetup().getScale();
        }
    }

    private static int ultimaFilaDelArea(XSSFWorkbook libro, int indiceHoja) {
        return new org.apache.poi.ss.util.AreaReference(libro.getPrintArea(indiceHoja),
                org.apache.poi.ss.SpreadsheetVersion.EXCEL2007).getLastCell().getRow();
    }

    private static int ultimaColumnaDelArea(XSSFWorkbook libro, int indiceHoja) {
        return new org.apache.poi.ss.util.AreaReference(libro.getPrintArea(indiceHoja),
                org.apache.poi.ss.SpreadsheetVersion.EXCEL2007).getLastCell().getCol();
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

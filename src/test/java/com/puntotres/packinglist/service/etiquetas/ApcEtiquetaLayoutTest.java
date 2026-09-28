package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ApcEtiquetaLayoutTest {

    private static final List<ApcEtiquetaLayout> TODOS = List.of(
            ApcEtiquetaLayout.JAPAN, ApcEtiquetaLayout.KOREA, ApcEtiquetaLayout.USA,
            ApcEtiquetaLayout.WH_CROSSLOG, ApcEtiquetaLayout.RETAIL);

    @Test
    void resuelveLosNombresDeDestinoConocidosYRechazaElResto() {
        // Acepta tanto la clave de la config de packing ("D. USA", "WHOLESALE")
        // como el nombre del fichero de plantilla ("USA", "WH CROSSLOG").
        assertSame(ApcEtiquetaLayout.JAPAN, ApcEtiquetaLayout.paraDestino(" japan ").orElseThrow());
        assertSame(ApcEtiquetaLayout.KOREA, ApcEtiquetaLayout.paraDestino("KOREA").orElseThrow());
        assertSame(ApcEtiquetaLayout.USA, ApcEtiquetaLayout.paraDestino("D. USA").orElseThrow());
        assertSame(ApcEtiquetaLayout.USA, ApcEtiquetaLayout.paraDestino("USA").orElseThrow());
        assertSame(ApcEtiquetaLayout.WH_CROSSLOG,
                ApcEtiquetaLayout.paraDestino("WHOLESALE").orElseThrow());
        assertSame(ApcEtiquetaLayout.WH_CROSSLOG, ApcEtiquetaLayout.paraDestino("WH CROSSLOG").orElseThrow());
        assertSame(ApcEtiquetaLayout.RETAIL, ApcEtiquetaLayout.paraDestino(" retail ").orElseThrow());
        assertTrue(ApcEtiquetaLayout.paraDestino("IVRY").isEmpty());
        assertTrue(ApcEtiquetaLayout.paraDestino(null).isEmpty());
    }

    /**
     * RETAIL y WHOLESALE van al mismo almacén (Crosslog) y el cliente solo
     * partió la plantilla para que se imprima RETAIL o WHOLESALE en la línea
     * DESTINATION, pero desde que se ajustó el reparto en el A4 sus dos
     * ficheros YA NO comparten maquetación. Antes RETAIL copiaba las
     * coordenadas de WH_CROSSLOG y eso era correcto; copiarlas hoy escribiría
     * cada valor una o dos filas por encima de su rótulo.
     */
    @Test
    void retailYWholesaleYaNoCompartenCoordenadasNiPlantilla() {
        ApcEtiquetaLayout retail = ApcEtiquetaLayout.RETAIL;
        ApcEtiquetaLayout wholesale = ApcEtiquetaLayout.WH_CROSSLOG;
        assertNotEquals(wholesale.alturaBloque(), retail.alturaBloque());
        assertNotEquals(wholesale.offsetSegundaEtiqueta(), retail.offsetSegundaEtiqueta());
        assertNotEquals(wholesale.filaOrder(), retail.filaOrder());
        assertNotEquals(wholesale.rutaPlantilla(), retail.rutaPlantilla());
        assertNotEquals(wholesale.hojaCajas(), retail.hojaCajas());
        assertNotEquals(wholesale.hojaPalet(), retail.hojaPalet());
    }

    /**
     * El bloque de cada plantilla cubre el par de etiquetas entero y la
     * segunda arranca donde dice offsetSegundaEtiqueta. La segunda suele venir
     * recortada por abajo (alturaBloque &lt; 2*offset) porque es donde acaba la
     * página: el bloque se copia entero, recorte incluido, así que lo que se
     * exige es que quepan las dos y que la segunda no se salga del bloque.
     */
    @Test
    void elBloqueCubreElParYLaSegundaEtiquetaArrancaDentro() throws IOException {
        for (ApcEtiquetaLayout layout : TODOS) {
            String donde = layout.rutaPlantilla() + ": ";
            assertTrue(layout.offsetSegundaEtiqueta() < layout.alturaBloque(),
                    donde + "la segunda etiqueta arranca fuera del bloque");
            assertTrue(layout.alturaBloque() <= 2 * layout.offsetSegundaEtiqueta(),
                    donde + "el bloque es más alto que las dos etiquetas");
            assertTrue(layout.filaPeso() + layout.offsetSegundaEtiqueta() < layout.alturaBloque(),
                    donde + "el peso de la segunda etiqueta cae fuera del bloque");
            try (InputStream plantilla = getClass().getResourceAsStream(layout.rutaPlantilla());
                 XSSFWorkbook libro = new XSSFWorkbook(plantilla)) {
                // La plantilla trae UN bloque y nada más: un segundo par
                // dentro saldría duplicado en cada libro generado.
                assertEquals(layout.alturaBloque() - 1,
                        libro.getSheet(layout.hojaCajas()).getLastRowNum(),
                        donde + "la hoja de cajas no es exactamente un bloque");
            }
        }
    }

    /**
     * La hoja de palet también está maquetada distinta en cada plantilla, y
     * en WHOLESALE el cliente trabaja sin la columna A: los valores caen en la
     * B y los rótulos en la A. Escribir en la C dejaría el número de cajas y
     * el peso fuera del recuadro y fuera del área de impresión.
     */
    @Test
    void cadaHojaDePaletEscribeEnLaFilaYLaColumnaDeSuRotulo() throws IOException {
        for (ApcEtiquetaLayout layout : TODOS) {
            ApcEtiquetaLayout.Palet geo = layout.palet();
            String donde = layout.rutaPlantilla() + ": ";
            try (InputStream plantilla = getClass().getResourceAsStream(layout.rutaPlantilla());
                 XSSFWorkbook libro = new XSSFWorkbook(plantilla)) {
                XSSFSheet hoja = libro.getSheet(layout.hojaPalet());
                assertEquals(filaDeRotulo(hoja, geo.colValor() - 1, "Nombre total de colis"),
                        geo.filaNumCajas(), donde + "nº de cajas del palet");
                assertEquals(filaDeRotulo(hoja, geo.colValor() - 1, "Poids brut"),
                        geo.filaPeso(), donde + "peso del palet");
                assertTrue(hoja.getLastRowNum() < geo.altura(),
                        donde + "la hoja de palet trae más de un bloque modelo");
            }
        }
        assertEquals(1, ApcEtiquetaLayout.WH_CROSSLOG.palet().colValor(),
                "en WHOLESALE los valores del palet van en la columna B");
    }

    private static int filaDeRotulo(XSSFSheet hoja, int colRotulo, String rotulo) {
        for (int fila = 0; fila <= hoja.getLastRowNum(); fila++) {
            Row f = hoja.getRow(fila);
            Cell etiqueta = (f == null) ? null : f.getCell(colRotulo);
            if (etiqueta != null && etiqueta.getCellType() == CellType.STRING
                    && etiqueta.getStringCellValue().trim().startsWith(rotulo)) {
                return fila;
            }
        }
        throw new AssertionError("La hoja no tiene el rótulo '" + rotulo + "'");
    }

    /**
     * Cada fila del layout es la de SU rótulo en la plantilla, resolviendo
     * las celdas combinadas: en la plantilla de USA la celda de valor de
     * Reference está combinada (C12:C13) y su rótulo cae en la fila de abajo,
     * así que apuntar a la fila del rótulo escribe en una celda tapada. Eso
     * no da error ni celda vacía: a la vista se queda la referencia de
     * ejemplo de la plantilla, que es de otro artículo.
     */
    @Test
    void cadaFilaEsLaDeSuRotuloResolviendoLasCeldasCombinadas() throws IOException {
        for (ApcEtiquetaLayout layout : TODOS) {
            try (InputStream plantilla = getClass().getResourceAsStream(layout.rutaPlantilla());
                 XSSFWorkbook libro = new XSSFWorkbook(plantilla)) {
                XSSFSheet hoja = libro.getSheet(layout.hojaCajas());
                String donde = layout.rutaPlantilla() + ": ";
                assertEquals(filaDeValor(hoja, "Order N"), layout.filaOrder(), donde + "Order N");
                assertEquals(filaDeValor(hoja, "Reference"), layout.filaReferencia(),
                        donde + "Reference");
                assertEquals(filaDeValor(hoja, "Colour"), layout.filaColor(), donde + "Colour");
                assertEquals(filaDeValor(hoja, "Size"), layout.filaTalla(), donde + "Size");
                assertEquals(filaDeValor(hoja, "Pieces by size"), layout.filaPiezas(),
                        donde + "Pieces by size");
                assertEquals(filaDeValor(hoja, "Colisage"), layout.filaColisage(),
                        donde + "Colisage");
                assertEquals(filaDeValor(hoja, "Poids brut"), layout.filaPeso(),
                        donde + "Poids brut");
            }
        }
    }

    /** Fila en la que hay que ESCRIBIR el valor del rótulo dado. */
    private static int filaDeValor(XSSFSheet hoja, String rotulo) {
        for (int fila = 0; fila <= hoja.getLastRowNum(); fila++) {
            Row f = hoja.getRow(fila);
            Cell etiqueta = (f == null) ? null : f.getCell(1);
            if (etiqueta == null || etiqueta.getCellType() != CellType.STRING
                    || !etiqueta.getStringCellValue().trim().startsWith(rotulo)) {
                continue;
            }
            // La celda de valor puede estar combinada hacia abajo: lo que se
            // escribe fuera de su esquina superior izquierda no se ve.
            for (CellRangeAddress region : hoja.getMergedRegions()) {
                if (region.isInRange(fila, ApcEtiquetaLayout.COL_VALOR)) {
                    return region.getFirstRow();
                }
            }
            return fila;
        }
        throw new AssertionError("La plantilla no tiene el rótulo '" + rotulo + "'");
    }

    @Test
    void cadaPlantillaExisteEnElClasspathYTieneSusDosHojas() throws IOException {
        for (ApcEtiquetaLayout layout : TODOS) {
            try (InputStream plantilla = getClass().getResourceAsStream(layout.rutaPlantilla())) {
                assertNotNull(plantilla, "Falta la plantilla " + layout.rutaPlantilla());
                try (XSSFWorkbook libro = new XSSFWorkbook(plantilla)) {
                    assertNotNull(libro.getSheet(layout.hojaCajas()),
                            layout.rutaPlantilla() + " sin hoja '" + layout.hojaCajas() + "'");
                    assertNotNull(libro.getSheet(layout.hojaPalet()),
                            layout.rutaPlantilla() + " sin hoja '" + layout.hojaPalet() + "'");
                }
            }
        }
    }
}

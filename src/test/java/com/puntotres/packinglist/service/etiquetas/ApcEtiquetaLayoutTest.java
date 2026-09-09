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

    @Test
    void retailYWholesaleCompartenCoordenadasPeroNoPlantilla() {
        // Las dos etiquetas van al mismo almacén (Crosslog) y el cliente solo
        // partió la plantilla para que se imprima RETAIL o WHOLESALE: la
        // maquetación es idéntica. Se ancla aquí porque, siendo iguales las
        // coordenadas, ninguna aserción de celdas detectaría que RETAIL apunta
        // al fichero equivocado.
        ApcEtiquetaLayout retail = ApcEtiquetaLayout.RETAIL;
        ApcEtiquetaLayout wholesale = ApcEtiquetaLayout.WH_CROSSLOG;
        assertEquals(wholesale.alturaBloque(), retail.alturaBloque());
        assertEquals(wholesale.offsetSegundaEtiqueta(), retail.offsetSegundaEtiqueta());
        assertEquals(wholesale.filaOrder(), retail.filaOrder());
        assertEquals(wholesale.filaLivraison(), retail.filaLivraison());
        assertEquals(wholesale.filaReferencia(), retail.filaReferencia());
        assertEquals(wholesale.filaColor(), retail.filaColor());
        assertEquals(wholesale.filaTalla(), retail.filaTalla());
        assertEquals(wholesale.filaPiezas(), retail.filaPiezas());
        assertEquals(wholesale.filaColisage(), retail.filaColisage());
        assertEquals(wholesale.filaPeso(), retail.filaPeso());
        assertNotEquals(wholesale.rutaPlantilla(), retail.rutaPlantilla());
        assertNotEquals(wholesale.hojaCajas(), retail.hojaCajas());
        assertNotEquals(wholesale.hojaPalet(), retail.hojaPalet());
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

package com.puntotres.packinglist.service.etiquetas;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Herramienta manual: convierte los excels de muestra AJUSTADOS A MANO por el
 * usuario (target/muestras-etiquetas-apc/, que deja ApcEtiquetasMuestrasTool)
 * en las plantillas de src/main/resources/client-labels/.
 *
 * <p>La muestra trae UN par de etiquetas de caja, que ya es el bloque modelo y
 * no hay nada que recortar, y DOS etiquetas de palet, de las que solo se
 * conserva la primera: el builder replica el bloque modelo por palet, así que
 * un segundo bloque dentro de la plantilla saldría duplicado en cada libro.
 *
 * <p>Se borran además los valores de la muestra —son datos de prueba
 * inventados, no del cliente— y los saltos de página, que el builder pone por
 * su cuenta según cuántas cajas y palets haya.
 *
 * <p>Se conserva por si hay que regenerar las plantillas desde muestras
 * nuevas. Ejecutar: mvn test -Dtest=PreparadorPlantillasApcTool
 */
class PreparadorPlantillasApcTool {

    private static final Path MUESTRAS = Path.of("target", "muestras-etiquetas-apc");
    private static final Path PLANTILLAS = Path.of("src", "main", "resources", "client-labels");

    @Test
    void prepararLasCinco() throws IOException {
        preparar("JAPAN", ApcEtiquetaLayout.JAPAN);
        preparar("KOREA", ApcEtiquetaLayout.KOREA);
        preparar("D. USA", ApcEtiquetaLayout.USA);
        preparar("WHOLESALE", ApcEtiquetaLayout.WH_CROSSLOG);
        preparar("RETAIL", ApcEtiquetaLayout.RETAIL);
    }

    private void preparar(String nombre, ApcEtiquetaLayout layout) throws IOException {
        Path muestra = MUESTRAS.resolve("Etiquetas APC " + nombre + ".xlsx");
        Path plantilla = PLANTILLAS.resolve(
                layout.rutaPlantilla().substring(layout.rutaPlantilla().lastIndexOf('/') + 1));
        try (FileInputStream in = new FileInputStream(muestra.toFile());
             XSSFWorkbook libro = new XSSFWorkbook(in)) {

            XSSFSheet cajas = libro.getSheet(layout.hojaCajas());
            quitarSaltos(cajas);
            for (int base : new int[] {0, layout.offsetSegundaEtiqueta()}) {
                for (int fila : new int[] {layout.filaOrder(), layout.filaLivraison(),
                        layout.filaReferencia(), layout.filaColor(), layout.filaTalla(),
                        layout.filaPiezas(), layout.filaColisage(), layout.filaPeso()}) {
                    blanquear(cajas, base + fila, layout.colValor());
                }
            }

            XSSFSheet palet = libro.getSheet(layout.hojaPalet());
            ApcEtiquetaLayout.Palet geo = layout.palet();
            quitarSaltos(palet);
            recortarA(palet, geo.altura());
            blanquear(palet, geo.filaNumCajas(), geo.colValor());
            blanquear(palet, geo.filaPeso(), geo.colValor());

            try (FileOutputStream out = new FileOutputStream(plantilla.toFile())) {
                libro.write(out);
            }
            System.out.println("escrita " + plantilla + ": cajas " + (cajas.getLastRowNum() + 1)
                    + " filas, palet " + (palet.getLastRowNum() + 1) + " filas");
        }
    }

    private static void blanquear(XSSFSheet hoja, int fila, int col) {
        XSSFRow f = hoja.getRow(fila);
        if (f != null && f.getCell(col) != null) {
            f.getCell(col).setBlank();
        }
    }

    private static void quitarSaltos(XSSFSheet hoja) {
        for (int salto : hoja.getRowBreaks()) {
            hoja.removeRowBreak(salto);
        }
    }

    /**
     * Deja la hoja con las primeras {@code altura} filas: fuera filas, celdas
     * combinadas e imágenes de más.
     */
    private static void recortarA(XSSFSheet hoja, int altura) {
        for (int f = hoja.getLastRowNum(); f >= altura; f--) {
            if (hoja.getRow(f) != null) {
                hoja.removeRow(hoja.getRow(f));
            }
        }
        List<Integer> sobran = new ArrayList<>();
        for (int i = 0; i < hoja.getNumMergedRegions(); i++) {
            CellRangeAddress merge = hoja.getMergedRegion(i);
            if (merge.getFirstRow() >= altura) {
                sobran.add(i);
            }
        }
        hoja.removeMergedRegions(sobran);
        XSSFDrawing dibujo = hoja.getDrawingPatriarch();
        if (dibujo == null) {
            return;
        }
        // POI no sabe borrar una imagen: se quitan sus anclajes del XML, de
        // atrás hacia delante para que los índices no se muevan.
        var xml = dibujo.getCTDrawing();
        for (int i = xml.sizeOfTwoCellAnchorArray() - 1; i >= 0; i--) {
            if (xml.getTwoCellAnchorArray(i).getFrom().getRow() >= altura) {
                xml.removeTwoCellAnchor(i);
            }
        }
        for (int i = xml.sizeOfOneCellAnchorArray() - 1; i >= 0; i--) {
            if (xml.getOneCellAnchorArray(i).getFrom().getRow() >= altura) {
                xml.removeOneCellAnchor(i);
            }
        }
    }
}

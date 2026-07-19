package com.puntotres.packinglist.plantillas;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Herramienta puntual (fuera del patrón de surefire) que generó las
 * plantillas limpias a partir de los ficheros originales del cliente:
 *
 * - ami-belts-packing-list-template.xlsx: quita las filas de ejemplo del
 *   original de cinturones dejando UNA fila modelo pegada a la de totales,
 *   como en la plantilla de bolsos.
 * - apc-packing-list-template.xlsx: extrae la primera hoja (en blanco) del
 *   apc-template.xlsx de 487 hojas y deja un solo bloque de palet modelo.
 * - generic-packing-list-template.xlsx: se queda con la hoja "INV 250121",
 *   sin valores de ejemplo y con fila de palet + fila de datos compactas.
 *
 * Se conserva por si hay que regenerarlas desde los originales (guardados
 * en docs/Packing Lists). Ejecutar: mvn test -Dtest=PreparadorPlantillasTool
 */
class PreparadorPlantillasTool {

    private static final String DIR = "src/main/resources/client-packinglist/";
    private static final String DIR_ORIGINALES = "docs/Packing Lists/";

    private String original(String nombre) {
        // Los originales pueden estar aún en resources (primera ejecución)
        // o ya movidos a docs/Packing Lists (regeneración).
        java.io.File enResources = new java.io.File(DIR + nombre);
        return enResources.exists() ? DIR + nombre : DIR_ORIGINALES + nombre;
    }

    @Test
    void prepararBelts() throws Exception {
        ZipSecureFile.setMaxFileCount(20000);
        try (FileInputStream in = new FileInputStream(original("ami-belts-packing-list-complete-example.xlsx"));
             XSSFWorkbook wb = new XSSFWorkbook(in)) {

            Sheet hoja = wb.getSheet("STANDARD PKL E25");

            // Cabecera: fuera fechas de ejemplo y la lista suelta de destinos.
            limpiarCeldas(hoja, 6, 1);   // B7 fecha factura
            limpiarCeldas(hoja, 8, 1);   // B9 fecha envío
            limpiarCeldas(hoja, 9, 1);   // B10 'Destination'
            limpiarCeldas(hoja, 10, 1);  // B11 France
            limpiarCeldas(hoja, 11, 1);  // B12 CHINA
            limpiarCeldas(hoja, 12, 1);  // B13 JAPAN

            // Fila 19 (idx 18) queda como fila modelo, vacía pero con estilos.
            vaciarFila(hoja, 18, 0, 20);

            // Fuera las filas de ejemplo/reserva 20-30 (idx 19-29): la fila de
            // totales (idx 30) sube hasta quedar pegada a la fila modelo.
            eliminarFilas(hoja, 19, 29);

            // El resumen queda con los contadores a cero como en bolsos.
            ponerCero(hoja, 23, 20);  // TOTAL NUMBER OF BOXES (U24 tras el desplazamiento)
            ponerCero(hoja, 26, 20);  // VOLUME (U27)

            guardar(wb, DIR + "ami-belts-packing-list-template.xlsx");
        }
    }

    /**
     * Regenera la plantilla APC desde el ejemplo real de bolsos+cinturones
     * (apc-bags-and-belts-complete-example.xlsx, hoja única con el layout
     * vigente: Nº COLIS en B:C, MODÈLE en D:E, SIZE en N, POIDS en O,
     * QUANTITE en P). Deja: fila modelo de palet (idx 16), fila modelo de
     * caja (idx 17, con sus fusiones), fila de total de peso (idx 18), fila
     * TOTAL (idx 19) y las 5 líneas de resumen (idx 21-25) sin valores.
     */
    @Test
    void prepararApc() throws Exception {
        ZipSecureFile.setMaxFileCount(20000);
        try (FileInputStream in = new FileInputStream(original("apc-bags-and-belts-complete-example.xlsx"));
             XSSFWorkbook wb = new XSSFWorkbook(in)) {

            Sheet hoja = wb.getSheetAt(0);
            wb.setSheetName(0, "PACKING LIST");

            // Cabecera de ejemplo: cliente, dirección, fecha y factura.
            limpiarCeldas(hoja, 7, 5);    // F8
            limpiarCeldas(hoja, 9, 5);    // F10
            limpiarCeldas(hoja, 11, 5);   // F12
            limpiarCeldas(hoja, 13, 15);  // P14

            // Fórmulas/valores que reescribe el builder (evita #REF tras
            // el desplazamiento): subtotal del palet modelo, total de peso,
            // total de unidades y las 5 líneas de texto del resumen.
            limpiarCeldas(hoja, 16, 14);   // O17
            limpiarCeldas(hoja, 100, 14);  // O101
            limpiarCeldas(hoja, 101, 15);  // P102
            for (int fila = 103; fila <= 107; fila++) {
                limpiarCeldas(hoja, fila, 3);  // D
                limpiarCeldas(hoja, fila, 4);  // E
            }

            // Fila modelo de caja (idx 17): vacía pero con estilos y fusiones.
            vaciarFila(hoja, 17, 1, 15);

            // Fuera el resto de filas de datos (idx 18-99): la fila de total
            // de peso, TOTAL y resumen suben pegadas al bloque modelo.
            eliminarFilas(hoja, 18, 99);

            guardar(wb, DIR + "apc-packing-list-template.xlsx");
        }
    }

    private void prepararGenerica() throws Exception {
        try (FileInputStream in = new FileInputStream(original("generic-client-template.xlsx"));
             XSSFWorkbook wb = new XSSFWorkbook(in)) {

            wb.removeSheetAt(wb.getSheetIndex("INV 220031"));
            wb.removeSheetAt(wb.getSheetIndex("Hoja1"));
            Sheet hoja = wb.getSheetAt(0);
            wb.setSheetName(0, "PACKING LIST");

            // Cabecera y SHIPMENT DETAILS de ejemplo.
            limpiarCeldas(hoja, 9, 2);   // C10 cliente
            limpiarCeldas(hoja, 11, 2);  // C12 destino
            limpiarCeldas(hoja, 13, 2);  // C14 fecha
            limpiarCeldas(hoja, 15, 2);  // C16 factura
            for (int fila = 11; fila <= 16; fila++) {
                limpiarCeldas(hoja, fila, 8); // I12-I17
            }

            // Filas modelo sin valores: palet (idx 20) y datos (idx 22).
            vaciarFila(hoja, 20, 1, 8);
            vaciarFila(hoja, 22, 1, 8);
            // Fórmulas del TOTAL (idx 24): las reescribe el builder.
            limpiarCeldas(hoja, 24, 6);  // G25
            limpiarCeldas(hoja, 24, 7);  // H25

            // Compactar: fuera las filas en blanco entre palet, datos y TOTAL.
            eliminarFilas(hoja, 23, 23);
            eliminarFilas(hoja, 21, 21);

            guardar(wb, DIR + "generic-packing-list-template.xlsx");
        }
    }

    // --- helpers ---

    /** Borra el contenido (no el estilo) de una celda si existe. */
    private void limpiarCeldas(Sheet hoja, int idxFila, int idxCol) {
        Row fila = hoja.getRow(idxFila);
        if (fila == null) {
            return;
        }
        Cell celda = fila.getCell(idxCol);
        if (celda != null) {
            celda.setBlank();
        }
    }

    private void vaciarFila(Sheet hoja, int idxFila, int desdeCol, int hastaCol) {
        for (int col = desdeCol; col <= hastaCol; col++) {
            limpiarCeldas(hoja, idxFila, col);
        }
    }

    private void ponerCero(Sheet hoja, int idxFila, int idxCol) {
        Row fila = hoja.getRow(idxFila);
        if (fila != null && fila.getCell(idxCol) != null) {
            fila.getCell(idxCol).setCellValue(0);
        }
    }

    /** Elimina las filas idxDesde..idxHasta (incluidas) subiendo el resto. */
    private void eliminarFilas(Sheet hoja, int idxDesde, int idxHasta) {
        // Primero fuera los merges contenidos en las filas a eliminar, que
        // shiftRows no sabe recolocar.
        List<Integer> mergesAEliminar = new ArrayList<>();
        for (int i = 0; i < hoja.getNumMergedRegions(); i++) {
            CellRangeAddress merge = hoja.getMergedRegion(i);
            if (merge.getFirstRow() >= idxDesde && merge.getLastRow() <= idxHasta) {
                mergesAEliminar.add(i);
            }
        }
        for (int i = mergesAEliminar.size() - 1; i >= 0; i--) {
            hoja.removeMergedRegion(mergesAEliminar.get(i));
        }

        for (int idx = idxDesde; idx <= idxHasta; idx++) {
            Row fila = hoja.getRow(idx);
            if (fila != null) {
                hoja.removeRow(fila);
            }
        }
        int numFilas = idxHasta - idxDesde + 1;
        hoja.shiftRows(idxHasta + 1, hoja.getLastRowNum(), -numFilas);
    }

    private void guardar(XSSFWorkbook wb, String destino) throws Exception {
        try (FileOutputStream out = new FileOutputStream(destino)) {
            wb.write(out);
        }
        System.out.println("Plantilla generada: " + destino);
    }
}

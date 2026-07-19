package com.puntotres.packinglist.plantillas;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Herramienta puntual (no es un test de regresión: el sufijo "Tool" la deja
 * fuera del patrón de surefire). Vuelca la estructura de las plantillas xlsx
 * a un fichero de texto para poder diseñar los builders y la limpieza.
 *
 * Ejecutar con: mvn test -Dtest=InspectorPlantillasTool
 */
class InspectorPlantillasTool {

    private static final String DIR = "src/main/resources/client-packinglist/";
    private static final String SALIDA =
            "C:/Users/jordi/AppData/Local/Temp/claude/c--Users-jordi-Documents-Workspace-Packing-List-Automation/"
            + "6737a73b-070b-4c96-adaf-87eaddc973a2/scratchpad/plantillas-dump.txt";

    @Test
    void volcarPlantillas() throws Exception {
        // El apc-template.xlsx trae 487 hojas y supera el límite de entradas
        // zip con el que POI se protege de ficheros maliciosos.
        org.apache.poi.openxml4j.util.ZipSecureFile.setMaxFileCount(20000);
        try (PrintWriter out = new PrintWriter(SALIDA, StandardCharsets.UTF_8)) {
            volcar(out, DIR + "apc-packing-list-template.xlsx", 0, 30);
        }
    }

    private void volcar(PrintWriter out, String fichero, Integer soloHoja, int maxFilas) throws Exception {
        out.println("################ " + fichero);
        try (FileInputStream in = new FileInputStream(fichero);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {

            out.println("hojas=" + wb.getNumberOfSheets());
            int desde = (soloHoja != null) ? soloHoja : 0;
            int hasta = (soloHoja != null) ? soloHoja : Math.min(wb.getNumberOfSheets() - 1, 3);
            for (int i = 0; i <= Math.min(wb.getNumberOfSheets() - 1, 9); i++) {
                out.println("  hoja[" + i + "]='" + wb.getSheetName(i) + "'"
                        + (wb.isSheetHidden(i) ? " (oculta)" : ""));
            }
            for (int i = desde; i <= hasta; i++) {
                volcarHoja(out, wb.getSheetAt(i), maxFilas);
            }
        }
        out.println();
    }

    private void volcarHoja(PrintWriter out, Sheet hoja, int maxFilas) {
        out.println("---- hoja '" + hoja.getSheetName() + "' lastRow=" + hoja.getLastRowNum());
        out.print("merges:");
        for (CellRangeAddress merge : hoja.getMergedRegions()) {
            out.print(" " + merge.formatAsString());
        }
        out.println();
        for (DataValidation dv : ((XSSFSheet) hoja).getDataValidations()) {
            out.println("validation: " + dv.getRegions().getCellRangeAddresses()[0].formatAsString()
                    + " -> " + dv.getValidationConstraint().getFormula1());
        }
        for (int r = 0; r <= Math.min(hoja.getLastRowNum(), maxFilas); r++) {
            Row fila = hoja.getRow(r);
            if (fila == null) {
                continue;
            }
            StringBuilder linea = new StringBuilder();
            for (Cell celda : fila) {
                String valor = describir(celda);
                if (valor != null) {
                    linea.append(" ").append(new CellReference(celda).formatAsString())
                            .append("=").append(valor);
                }
            }
            if (linea.length() > 0) {
                out.println("fila " + (r + 1) + " (idx " + r + "):" + linea);
            }
        }
    }

    private String describir(Cell celda) {
        CellType tipo = celda.getCellType();
        if (tipo == CellType.BLANK) {
            return null;
        }
        if (tipo == CellType.STRING) {
            String texto = celda.getStringCellValue();
            return texto.isBlank() ? null : "'" + texto.replace("\n", "\\n") + "'";
        }
        if (tipo == CellType.NUMERIC) {
            return String.valueOf(celda.getNumericCellValue());
        }
        if (tipo == CellType.FORMULA) {
            return "={" + celda.getCellFormula() + "}";
        }
        return tipo.toString();
    }
}

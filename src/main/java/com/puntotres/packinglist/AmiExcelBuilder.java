package com.puntotres.packinglist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Genera el Excel de packing list AMI rellenando la plantilla
 * "STANDARD PKL H26" incluida en el classpath.
 *
 * La plantilla trae la cabecera, la leyenda de tallas, los encabezados de
 * columna (fila 19), UNA fila modelo con los estilos correctos (fila 20),
 * la fila de totales (fila 21) y el bloque resumen (filas 23-28). Este
 * builder escribe la cabecera, clona la fila modelo tantas veces como cajas
 * haya (desplazando totales y resumen hacia abajo) y reescribe las fórmulas
 * de totales sobre el rango real de filas usadas.
 */
@Service
public class AmiExcelBuilder {

    private static final String RUTA_PLANTILLA = "/client-packinglist/ami-bags-packing-list-template.xlsx";
    private static final String NOMBRE_HOJA = "STANDARD PKL H26";

    // Datos fijos del proveedor: no vienen en el JSON.
    private static final String PROVEEDOR_NOMBRE = "PUNTOTRES";
    private static final String PROVEEDOR_CODIGO = "PUN";
    private static final String PROVEEDOR_CIUDAD = "BADALONA";
    private static final String PROVEEDOR_PAIS = "SPAIN";

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Índices 0-based de POI (fila 20 de Excel = índice 19).
    private static final int IDX_FILA_MODELO = 19;
    private static final int IDX_FILA_TOTALES = 20;
    private static final int IDX_RESUMEN_TOTAL_QTY = 23;   // V24
    private static final int IDX_RESUMEN_NUM_CAJAS = 24;   // V25
    private static final int IDX_RESUMEN_PESO_BRUTO = 25;  // V26
    private static final int IDX_RESUMEN_PESO_NETO = 26;   // V27
    private static final int IDX_RESUMEN_VOLUMEN = 27;     // V28

    // Índices 0-based de columna (A=0 ... V=21).
    private static final int COL_TEMPORADA = 0;      // A SEASON
    private static final int COL_PEDIDO = 1;         // B ORDER FORM NUMBER
    private static final int COL_REFERENCIA = 2;     // C REFERENCE
    private static final int COL_COLOR = 3;          // D color CODE
    private static final int COL_NUM_CAJA = 4;       // E CTN NO.
    private static final int COL_SIZE_GRID = 5;      // F SIZE GRID
    private static final int COL_PRIMERA_TALLA = 6;  // G (talla "U" del grid de talla única)
    private static final int COL_ULTIMA_TALLA = 17;  // R
    private static final int COL_QNTY_TOTAL = 18;    // S
    private static final int COL_TAMANO_CAJA = 19;   // T SIZE OF BOX
    private static final int COL_PESO_NETO = 20;     // U NET.W/KGS
    private static final int COL_PESO_BRUTO = 21;    // V GROSS.W/KGS
    private static final int ULTIMA_COLUMNA = COL_PESO_BRUTO;

    /**
     * Rellena la plantilla con los datos recibidos y devuelve el .xlsx
     * resultante como bytes, listo para guardar en disco o servir por HTTP.
     */
    public byte[] generar(PackingListData data) throws IOException {
        try (InputStream plantilla = abrirPlantilla();
             Workbook wb = new XSSFWorkbook(plantilla);
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            Sheet hoja = wb.getSheet(NOMBRE_HOJA);
            if (hoja == null) {
                throw new IllegalStateException("La plantilla no contiene la hoja '" + NOMBRE_HOJA + "'");
            }

            escribirCabecera(hoja, data);
            int idxFilaTotales = escribirFilasDeCajas(hoja, data);
            escribirTotales(hoja, idxFilaTotales, data.getCajas().size());
            escribirResumen(hoja, idxFilaTotales, data.getCajas());

            // Los valores cacheados de la plantilla son 0; con esto Excel
            // recalcula todas las fórmulas al abrir el fichero.
            wb.setForceFormulaRecalculation(true);

            wb.write(salida);
            return salida.toByteArray();
        }
    }

    private InputStream abrirPlantilla() {
        InputStream in = getClass().getResourceAsStream(RUTA_PLANTILLA);
        if (in == null) {
            throw new IllegalStateException("No se encuentra la plantilla en el classpath: " + RUTA_PLANTILLA);
        }
        return in;
    }

    /**
     * Cabecera fija (filas 3-10). El proveedor se reescribe desde las
     * constantes aunque la plantilla ya lo traiga, por robustez ante
     * futuros cambios de plantilla.
     */
    private void escribirCabecera(Sheet hoja, PackingListData data) {
        escribirTexto(hoja, 2, 1, PROVEEDOR_NOMBRE);   // B3
        escribirTexto(hoja, 2, 4, PROVEEDOR_CIUDAD);   // E3
        escribirTexto(hoja, 3, 1, PROVEEDOR_CODIGO);   // B4
        escribirTexto(hoja, 3, 4, PROVEEDOR_PAIS);     // E4

        escribirTexto(hoja, 5, 1, data.getNumeroFactura());        // B6
        escribirFecha(hoja, 6, 1, data.getFechaFactura());         // B7
        escribirFecha(hoja, 8, 1, data.getFechaEnvio());           // B9
        escribirTexto(hoja, 9, 1, data.getDestino());              // B10
    }

    /**
     * Escribe una fila por caja a partir de la fila modelo (20).
     *
     * Con N cajas: primero se desplazan N-1 posiciones hacia abajo todas las
     * filas desde la de totales (shiftRows mueve celdas, estilos y ajusta las
     * fórmulas), dejando hueco justo debajo de la fila modelo. La primera caja
     * reutiliza la propia fila modelo; el resto son filas nuevas a las que se
     * les aplica el estilo de la fila modelo celda a celda.
     *
     * @return índice 0-based de la fila de totales tras el desplazamiento
     */
    private int escribirFilasDeCajas(Sheet hoja, PackingListData data) {
        List<PackingListData.Caja> cajas = data.getCajas();
        if (cajas == null || cajas.isEmpty()) {
            throw new IllegalArgumentException("El packing list no contiene cajas");
        }
        int numCajas = cajas.size();

        Row filaModelo = hoja.getRow(IDX_FILA_MODELO);

        // Los estilos del modelo se capturan ANTES de escribir nada en él.
        CellStyle[] estilosModelo = new CellStyle[ULTIMA_COLUMNA + 1];
        for (int col = 0; col <= ULTIMA_COLUMNA; col++) {
            Cell celda = filaModelo.getCell(col);
            estilosModelo[col] = (celda != null) ? celda.getCellStyle() : null;
        }
        short alturaModelo = filaModelo.getHeight();

        if (numCajas > 1) {
            hoja.shiftRows(IDX_FILA_TOTALES, hoja.getLastRowNum(), numCajas - 1);
        }

        for (int i = 0; i < numCajas; i++) {
            int idxFila = IDX_FILA_MODELO + i;
            Row fila = (i == 0) ? filaModelo : hoja.createRow(idxFila);
            if (i > 0) {
                fila.setHeight(alturaModelo);
                for (int col = 0; col <= ULTIMA_COLUMNA; col++) {
                    if (estilosModelo[col] != null) {
                        // Mismo workbook: se reutiliza la referencia al estilo,
                        // no hace falta clonarlo.
                        fila.createCell(col).setCellStyle(estilosModelo[col]);
                    }
                }
            }
            escribirCaja(fila, data, cajas.get(i));
        }

        return IDX_FILA_MODELO + numCajas;
    }

    private void escribirCaja(Row fila, PackingListData data, PackingListData.Caja caja) {
        fila.getCell(COL_TEMPORADA).setCellValue(data.getTemporada());
        fila.getCell(COL_PEDIDO).setCellValue(caja.getNumeroPedido());
        fila.getCell(COL_REFERENCIA).setCellValue(caja.getReferencia());
        fila.getCell(COL_COLOR).setCellValue(caja.getCodigoColor());
        fila.getCell(COL_NUM_CAJA).setCellValue(caja.getNumeroCaja());
        fila.getCell(COL_SIZE_GRID).setCellValue("U");
        fila.getCell(COL_PRIMERA_TALLA).setCellValue(caja.getCantidad());

        // Fórmula de fila igual que en el original: =SUM(G20:R20), etc.
        // Las filas de fórmula son 1-based, de ahí el +1.
        int filaExcel = fila.getRowNum() + 1;
        fila.getCell(COL_QNTY_TOTAL).setCellFormula("SUM(G" + filaExcel + ":R" + filaExcel + ")");

        fila.getCell(COL_TAMANO_CAJA).setCellValue(caja.getTamanoCaja());
        // Pesos desconocidos (null): la celda se deja vacía, pendiente de revisión.
        if (caja.getPesoNetoKg() != null) {
            fila.getCell(COL_PESO_NETO).setCellValue(caja.getPesoNetoKg());
        }
        if (caja.getPesoBrutoKg() != null) {
            fila.getCell(COL_PESO_BRUTO).setCellValue(caja.getPesoBrutoKg());
        }
    }

    /**
     * Reescribe la fila de totales con fórmulas SUM sobre el rango real de
     * filas de datos (la plantilla solo sumaba su única fila de ejemplo).
     */
    private void escribirTotales(Sheet hoja, int idxFilaTotales, int numCajas) {
        Row filaTotales = hoja.getRow(idxFilaTotales);
        int primeraFilaExcel = IDX_FILA_MODELO + 1;              // 20
        int ultimaFilaExcel = IDX_FILA_MODELO + numCajas;        // 19 + N

        for (int col = COL_PRIMERA_TALLA; col <= COL_QNTY_TOTAL; col++) {
            escribirSuma(filaTotales, col, primeraFilaExcel, ultimaFilaExcel);
        }
        escribirSuma(filaTotales, COL_PESO_NETO, primeraFilaExcel, ultimaFilaExcel);
        escribirSuma(filaTotales, COL_PESO_BRUTO, primeraFilaExcel, ultimaFilaExcel);
    }

    private void escribirSuma(Row fila, int col, int primeraFilaExcel, int ultimaFilaExcel) {
        String letra = CellReference.convertNumToColString(col);
        fila.getCell(col).setCellFormula(
                "SUM(" + letra + primeraFilaExcel + ":" + letra + ultimaFilaExcel + ")");
    }

    /**
     * Bloque SUM UP (columna V). Las fórmulas apuntan a la fila de totales ya
     * desplazada; el número de cajas y el volumen van como valor, igual que
     * en el original.
     */
    private void escribirResumen(Sheet hoja, int idxFilaTotales, List<PackingListData.Caja> cajas) {
        int desplazamiento = cajas.size() - 1;
        int filaTotalesExcel = idxFilaTotales + 1;

        celda(hoja, IDX_RESUMEN_TOTAL_QTY + desplazamiento, COL_PESO_BRUTO)
                .setCellFormula("+S" + filaTotalesExcel);
        celda(hoja, IDX_RESUMEN_NUM_CAJAS + desplazamiento, COL_PESO_BRUTO)
                .setCellValue(cajas.size());
        celda(hoja, IDX_RESUMEN_PESO_BRUTO + desplazamiento, COL_PESO_BRUTO)
                .setCellFormula("V" + filaTotalesExcel);
        celda(hoja, IDX_RESUMEN_PESO_NETO + desplazamiento, COL_PESO_BRUTO)
                .setCellFormula("U" + filaTotalesExcel);
        celda(hoja, IDX_RESUMEN_VOLUMEN + desplazamiento, COL_PESO_BRUTO)
                .setCellValue(calcularVolumenTotalM3(cajas));
    }

    /**
     * Suma del volumen de cada caja en m3: las dimensiones llegan en cm
     * ("LxWxH", p. ej. "60x40x30"), se pasan a metros y se multiplican.
     */
    private double calcularVolumenTotalM3(List<PackingListData.Caja> cajas) {
        double total = 0;
        for (PackingListData.Caja caja : cajas) {
            String[] dimensiones = caja.getTamanoCaja().split("[xX]");
            if (dimensiones.length != 3) {
                throw new IllegalArgumentException(
                        "tamanoCaja debe tener formato LxWxH en cm, recibido: " + caja.getTamanoCaja());
            }
            double volumen = 1;
            for (String dimension : dimensiones) {
                volumen *= Double.parseDouble(dimension.trim()) / 100.0;
            }
            total += volumen;
        }
        return total;
    }

    // --- Helpers de celdas: escriben el valor sin tocar el estilo existente ---

    private void escribirTexto(Sheet hoja, int idxFila, int idxCol, String valor) {
        celda(hoja, idxFila, idxCol).setCellValue(valor);
    }

    private void escribirFecha(Sheet hoja, int idxFila, int idxCol, String fecha) {
        LocalDate valor = LocalDate.parse(fecha, FORMATO_FECHA);
        // La celda de la plantilla ya tiene formato D/M/YYYY: al escribir un
        // LocalDate, Excel la muestra como fecha sin tocar el estilo.
        celda(hoja, idxFila, idxCol).setCellValue(valor);
    }

    private Cell celda(Sheet hoja, int idxFila, int idxCol) {
        Row fila = hoja.getRow(idxFila);
        if (fila == null) {
            fila = hoja.createRow(idxFila);
        }
        Cell celda = fila.getCell(idxCol);
        if (celda == null) {
            celda = fila.createCell(idxCol);
        }
        return celda;
    }
}

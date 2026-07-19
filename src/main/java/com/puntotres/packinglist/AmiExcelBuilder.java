package com.puntotres.packinglist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Genera el Excel de packing list AMI rellenando la plantilla "STANDARD
 * PKL" del {@link AmiLayout} indicado: bolsos/carteras ({@link
 * AmiLayout#BAGS}, talla única "U") o cinturones ({@link AmiLayout#BELTS},
 * matriz de tallas 70-110). Ambas comparten cabecera y mecánica de
 * escritura; solo cambian plantilla, filas y columnas (ver AmiLayout).
 *
 * La plantilla trae la cabecera, la leyenda de tallas, los encabezados de
 * columna, UNA fila modelo con los estilos correctos, la fila de totales y
 * el bloque resumen. Este builder escribe la cabecera, clona la fila
 * modelo tantas veces como cajas haya (desplazando totales y resumen hacia
 * abajo) y reescribe las fórmulas de totales sobre el rango real de filas
 * usadas.
 */
@Service
public class AmiExcelBuilder {

    // Nombre y código del proveedor: fijos, no vienen en el JSON. La ciudad
    // y el país sí son datos (PackingListData, editables en la pantalla de
    // entrada) porque AMI los pide como campo variable de la cabecera.
    private static final String PROVEEDOR_NOMBRE = "PUNTOTRES";
    private static final String PROVEEDOR_CODIGO = "PUN";

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Bolsos y carteras (comportamiento histórico, plantilla por defecto). */
    public byte[] generar(PackingListData data) throws IOException {
        return generar(data, AmiLayout.BAGS);
    }

    /**
     * Rellena la plantilla del layout indicado con los datos recibidos y
     * devuelve el .xlsx resultante como bytes, listo para guardar en disco
     * o servir por HTTP.
     */
    public byte[] generar(PackingListData data, AmiLayout layout) throws IOException {
        try (InputStream plantilla = abrirPlantilla(layout);
             Workbook wb = new XSSFWorkbook(plantilla);
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            Sheet hoja = wb.getSheet(layout.nombreHoja());
            if (hoja == null) {
                throw new IllegalStateException("La plantilla no contiene la hoja '" + layout.nombreHoja() + "'");
            }

            escribirCabecera(hoja, data, layout);
            int idxFilaTotales = escribirFilasDeCajas(hoja, data, layout);
            escribirTotales(hoja, idxFilaTotales, data.getCajas().size(), layout);
            escribirResumen(hoja, idxFilaTotales, data.getCajas(), layout);

            // Los valores cacheados de la plantilla son 0; con esto Excel
            // recalcula todas las fórmulas al abrir el fichero.
            wb.setForceFormulaRecalculation(true);

            wb.write(salida);
            return salida.toByteArray();
        }
    }

    private InputStream abrirPlantilla(AmiLayout layout) {
        InputStream in = getClass().getResourceAsStream(layout.rutaPlantilla());
        if (in == null) {
            throw new IllegalStateException(
                    "No se encuentra la plantilla en el classpath: " + layout.rutaPlantilla());
        }
        return in;
    }

    /**
     * Cabecera fija (filas 3-10, idénticas en bolsos y cinturones). El
     * nombre/código del proveedor se reescribe desde las constantes aunque
     * la plantilla ya lo traiga, por robustez ante futuros cambios.
     */
    private void escribirCabecera(Sheet hoja, PackingListData data, AmiLayout layout) {
        escribirTexto(hoja, 2, 1, PROVEEDOR_NOMBRE);          // B3
        escribirTexto(hoja, 2, 4, data.getCiudadProveedor()); // E3
        escribirTexto(hoja, 3, 1, PROVEEDOR_CODIGO);          // B4
        escribirTexto(hoja, 3, 4, data.getPaisProveedor());   // E4

        escribirTexto(hoja, 5, 1, data.getNumeroFactura());        // B6
        escribirFecha(hoja, 6, 1, data.getFechaFactura());         // B7
        escribirFecha(hoja, 8, 1, data.getFechaEnvio());           // B9
        escribirTexto(hoja, 9, 1, data.getDestino());              // B10
    }

    /**
     * Escribe una fila por caja a partir de la fila modelo del layout.
     *
     * Con N cajas: primero se desplazan N-1 posiciones hacia abajo todas las
     * filas desde la de totales (shiftRows mueve celdas, estilos y ajusta las
     * fórmulas), dejando hueco justo debajo de la fila modelo. La primera caja
     * reutiliza la propia fila modelo; el resto son filas nuevas a las que se
     * les aplica el estilo de la fila modelo celda a celda.
     *
     * @return índice 0-based de la fila de totales tras el desplazamiento
     */
    private int escribirFilasDeCajas(Sheet hoja, PackingListData data, AmiLayout layout) {
        List<PackingListData.Caja> cajas = data.getCajas();
        if (cajas == null || cajas.isEmpty()) {
            throw new IllegalArgumentException("El packing list no contiene cajas");
        }
        int numCajas = cajas.size();

        Row filaModelo = hoja.getRow(layout.idxFilaModelo());

        // Los estilos del modelo se capturan ANTES de escribir nada en él.
        CellStyle[] estilosModelo = new CellStyle[layout.ultimaColumna() + 1];
        for (int col = 0; col <= layout.ultimaColumna(); col++) {
            Cell celda = filaModelo.getCell(col);
            estilosModelo[col] = (celda != null) ? celda.getCellStyle() : null;
        }
        short alturaModelo = filaModelo.getHeight();

        if (numCajas > 1) {
            hoja.shiftRows(layout.idxFilaTotales(), hoja.getLastRowNum(), numCajas - 1);
        }

        for (int i = 0; i < numCajas; i++) {
            int idxFila = layout.idxFilaModelo() + i;
            Row fila = (i == 0) ? filaModelo : hoja.createRow(idxFila);
            if (i > 0) {
                fila.setHeight(alturaModelo);
                for (int col = 0; col <= layout.ultimaColumna(); col++) {
                    if (estilosModelo[col] != null) {
                        // Mismo workbook: se reutiliza la referencia al estilo,
                        // no hace falta clonarlo.
                        fila.createCell(col).setCellStyle(estilosModelo[col]);
                    }
                }
            }
            escribirCaja(fila, data, cajas.get(i), layout);
        }

        return layout.idxFilaModelo() + numCajas;
    }

    private void escribirCaja(Row fila, PackingListData data, PackingListData.Caja caja, AmiLayout layout) {
        fila.getCell(layout.colTemporada()).setCellValue(data.getTemporada());
        fila.getCell(layout.colPedido()).setCellValue(caja.getNumeroPedido());
        fila.getCell(layout.colReferencia()).setCellValue(caja.getReferencia());
        fila.getCell(layout.colColor()).setCellValue(caja.getCodigoColor());
        fila.getCell(layout.colNumCaja()).setCellValue(caja.getNumeroCaja());
        escribirTallas(fila, caja, layout);

        // Fórmula de fila igual que en el original: =SUM(G20:R20), etc.
        // Las filas de fórmula son 1-based, de ahí el +1.
        int filaExcel = fila.getRowNum() + 1;
        String colPrimeraTallaLetra = CellReference.convertNumToColString(layout.colPrimeraTalla());
        String colUltimaTallaLetra = CellReference.convertNumToColString(layout.colUltimaTalla());
        fila.getCell(layout.colQntyTotal()).setCellFormula(
                "SUM(" + colPrimeraTallaLetra + filaExcel + ":" + colUltimaTallaLetra + filaExcel + ")");

        fila.getCell(layout.colTamanoCaja()).setCellValue(caja.getTamanoCaja());
        // Pesos desconocidos (null): la celda se deja vacía, pendiente de revisión.
        if (caja.getPesoNetoKg() != null) {
            fila.getCell(layout.colPesoNeto()).setCellValue(caja.getPesoNetoKg());
        }
        if (caja.getPesoBrutoKg() != null) {
            fila.getCell(layout.colPesoBruto()).setCellValue(caja.getPesoBrutoKg());
        }
    }

    /**
     * Talla única "U" (bolsos, {@code cantidadesPorTalla == null}): la
     * cantidad de la caja va entera en la primera columna de talla.
     *
     * Matriz de tallas (cinturones): cada talla de la caja va en SU columna
     * (una caja física puede mezclar tallas); el grid se etiqueta con las
     * tallas presentes unidas por "-", en el mismo orden del mapa.
     */
    private void escribirTallas(Row fila, PackingListData.Caja caja, AmiLayout layout) {
        Map<String, Integer> cantidadesPorTalla = caja.getCantidadesPorTalla();
        if (cantidadesPorTalla == null) {
            fila.getCell(layout.colSizeGrid()).setCellValue("U");
            fila.getCell(layout.colPrimeraTalla()).setCellValue(caja.getCantidad());
            return;
        }

        fila.getCell(layout.colSizeGrid()).setCellValue(String.join("-", cantidadesPorTalla.keySet()));
        for (Map.Entry<String, Integer> entrada : cantidadesPorTalla.entrySet()) {
            Integer columna = layout.columnaPorTalla().get(entrada.getKey());
            if (columna == null) {
                throw new IllegalArgumentException(
                        "Talla '" + entrada.getKey() + "' no está en la matriz de la plantilla ("
                        + layout.columnaPorTalla().keySet() + ")");
            }
            fila.getCell(columna).setCellValue(entrada.getValue());
        }
    }

    /**
     * Reescribe la fila de totales con fórmulas SUM sobre el rango real de
     * filas de datos (la plantilla solo sumaba su única fila de ejemplo).
     */
    private void escribirTotales(Sheet hoja, int idxFilaTotales, int numCajas, AmiLayout layout) {
        Row filaTotales = hoja.getRow(idxFilaTotales);
        int primeraFilaExcel = layout.idxFilaModelo() + 1;
        int ultimaFilaExcel = layout.idxFilaModelo() + numCajas;

        for (int col = layout.colPrimeraTalla(); col <= layout.colQntyTotal(); col++) {
            escribirSuma(filaTotales, col, primeraFilaExcel, ultimaFilaExcel);
        }
        escribirSuma(filaTotales, layout.colPesoNeto(), primeraFilaExcel, ultimaFilaExcel);
        escribirSuma(filaTotales, layout.colPesoBruto(), primeraFilaExcel, ultimaFilaExcel);
    }

    private void escribirSuma(Row fila, int col, int primeraFilaExcel, int ultimaFilaExcel) {
        String letra = CellReference.convertNumToColString(col);
        fila.getCell(col).setCellFormula(
                "SUM(" + letra + primeraFilaExcel + ":" + letra + ultimaFilaExcel + ")");
    }

    /**
     * Bloque SUM UP. Las fórmulas apuntan a la fila de totales ya
     * desplazada; el número de cajas y el volumen van como valor, igual que
     * en el original.
     */
    private void escribirResumen(Sheet hoja, int idxFilaTotales, List<PackingListData.Caja> cajas, AmiLayout layout) {
        int desplazamiento = cajas.size() - 1;
        int filaTotalesExcel = idxFilaTotales + 1;
        String colQntyTotalLetra = CellReference.convertNumToColString(layout.colQntyTotal());
        String colPesoBrutoLetra = CellReference.convertNumToColString(layout.colPesoBruto());
        String colPesoNetoLetra = CellReference.convertNumToColString(layout.colPesoNeto());
        int col = layout.colPesoBruto();

        celda(hoja, layout.idxResumenTotalQty() + desplazamiento, col)
                .setCellFormula("+" + colQntyTotalLetra + filaTotalesExcel);
        celda(hoja, layout.idxResumenNumCajas() + desplazamiento, col)
                .setCellValue(cajas.size());
        celda(hoja, layout.idxResumenPesoBruto() + desplazamiento, col)
                .setCellFormula(colPesoBrutoLetra + filaTotalesExcel);
        celda(hoja, layout.idxResumenPesoNeto() + desplazamiento, col)
                .setCellFormula(colPesoNetoLetra + filaTotalesExcel);
        celda(hoja, layout.idxResumenVolumen() + desplazamiento, col)
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

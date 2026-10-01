package com.puntotres.packinglist.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

import com.puntotres.packinglist.ResumenUtil;
import com.puntotres.packinglist.VolumenUtil;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.CajaFisica;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

/**
 * Genera el packing list con la plantilla estándar Puntotres ("INV 250121"
 * del ejemplo del cliente): UN excel por destino, cajas agrupadas por
 * palet ("PALET n" + peso/medidas) y una fila por caja física.
 *
 * Vale para cualquier cliente nuevo que no tenga plantilla propia: solo
 * hace falta darlo de alta en application.yml (nombreLegal +
 * direccionEntrega) con plantilla GENERIC, sin tocar código.
 *
 * A diferencia de la plantilla real del cliente (que colapsa rangos de
 * cajas idénticas en una sola fila, p. ej. "1 - 16"), esta primera versión
 * escribe una fila por caja física: más simple y siempre correcta, aunque
 * más larga para envíos con muchas cajas idénticas. Colapsar rangos queda
 * pendiente como mejora futura.
 */
@Service
public class GenericoExcelBuilder implements GeneradorPackingListCliente {

    private static final String RUTA_PLANTILLA = "/client-packinglist/generic-packing-list-template.xlsx";
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /**
     * Formato de las celdas de SHIPMENT DETAILS que llevan fórmula. Antes eran
     * texto con la unidad pegada ("29.16 Kg"); ahora son números calculados
     * por Excel, y la unidad la pone el formato para que se siga leyendo igual.
     */
    private static final String FORMATO_KG = "0.00\" Kg\"";
    private static final String FORMATO_M3 = "0.00\" m3\"";

    // Índices 0-based de fila de la plantilla limpia.
    private static final int IDX_FILA_PALET_MODELO = 20; // fila 21
    private static final int IDX_FILA_DATO_MODELO = 21;  // fila 22
    private static final int IDX_FILA_TOTAL_MODELO = 22; // fila 23

    // Índices 0-based de columna.
    private static final int COL_BOX_NUM = 1;    // B
    private static final int COL_REFERENCIA = 2; // C
    private static final int COL_MODELO = 3;     // D
    private static final int COL_TEMPORADA = 4;  // E
    private static final int COL_COLOR = 5;      // F
    private static final int COL_UNIDADES = 6;   // G
    private static final int COL_PESO = 7;       // H
    private static final int COL_TAMANO = 8;     // I
    private static final int ULTIMA_COLUMNA = COL_TAMANO;

    @Override
    public TipoPlantilla tipo() {
        return TipoPlantilla.GENERIC;
    }

    @Override
    public List<ExcelGenerado> generar(DestinoData destino, List<PaletData> palets,
                                       DatosEnvio envio, ClienteConfig cliente) throws IOException {
        Map<Integer, List<CajaData>> porPalet = new LinkedHashMap<>();
        List<CajaData> sinPalet = new ArrayList<>();
        for (CajaData caja : destino.getCajas()) {
            if (CajaData.vaSuelta(caja.getNumeroPalet())) {
                sinPalet.add(caja);
            } else {
                porPalet.computeIfAbsent(caja.getNumeroPalet(), n -> new ArrayList<>()).add(caja);
            }
        }
        Map<Integer, String> medidasPorPalet = new LinkedHashMap<>();
        Map<Integer, Double> taraPorPalet = new LinkedHashMap<>();
        for (PaletData palet : palets) {
            medidasPorPalet.put(palet.getNumeroPalet(), palet.getMedidas());
            taraPorPalet.put(palet.getNumeroPalet(), palet.taraOPorDefecto());
        }
        // Cada destinación lleva su factura: la tecleada en su cabecera de la
        // revisión o, si no hay, la del envío.
        String factura = envio.facturaPara(destino);

        try (InputStream plantilla = abrirPlantilla();
             Workbook wb = new XSSFWorkbook(plantilla);
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            Sheet hoja = wb.getSheetAt(0);
            wb.setSheetName(0, nombreHoja(factura));

            escribirCabecera(hoja, cliente, envio, factura);
            // Las cajas sin palet no aportan tara (no hay palet físico).
            List<String> rangosCajas = new ArrayList<>();
            int idxFilaTotal = escribirCuerpo(hoja, porPalet, sinPalet, medidasPorPalet, taraPorPalet,
                    envio, rangosCajas);
            escribirTotalYResumen(hoja, idxFilaTotal, destino, rangosCajas);

            wb.setForceFormulaRecalculation(true);
            wb.write(salida);

            String nombreFichero = ExcelGenerado.nombreXlsx("PKL", destino.getNombreDestino(), factura);
            return List.of(new ExcelGenerado(destino.getNombreDestino(), nombreFichero,
                    salida.toByteArray(), cajasPendientes(destino)));
        }
    }

    /**
     * Cajas que salen sin peso en el excel: una entrada por caja FÍSICA (no
     * por línea) cuya líder no trae peso. Solo se mira el bruto, que es lo
     * único que escribe esta plantilla.
     */
    private static List<CajaData> cajasPendientes(DestinoData destino) {
        return CajaFisica.agrupar(destino.getCajas()).stream()
                .filter(caja -> caja.pesoBrutoKg() == null)
                .map(CajaFisica::lider)
                .toList();
    }

    private InputStream abrirPlantilla() {
        InputStream in = getClass().getResourceAsStream(RUTA_PLANTILLA);
        if (in == null) {
            throw new IllegalStateException("No se encuentra la plantilla en el classpath: " + RUTA_PLANTILLA);
        }
        return in;
    }

    /** "INV FA-1", o "INV" a secas si la destinación va sin factura. */
    private String nombreHoja(String factura) {
        String nombre = factura == null || factura.isBlank() ? "INV" : "INV " + factura.trim();
        nombre = nombre.replaceAll("[\\\\/*?:\\[\\]]", "_");
        return nombre.length() > 31 ? nombre.substring(0, 31) : nombre;
    }

    private void escribirCabecera(Sheet hoja, ClienteConfig cliente, DatosEnvio envio, String factura) {
        celda(hoja, 9, 2).setCellValue(cliente.getNombreLegal());     // C10
        celda(hoja, 11, 2).setCellValue(cliente.getDireccionEntrega()); // C12
        celda(hoja, 13, 2).setCellValue(LocalDate.parse(envio.getFechaFactura(), FORMATO_FECHA)); // C14
        celda(hoja, 15, 2).setCellValue(factura);                     // C16
    }

    /**
     * Escribe los bloques (palet + cajas) desplazando lo que haya debajo
     * (TOTAL) según haga falta, igual mecánica que en {@code AmiExcelBuilder}
     * y {@code ApcExcelBuilder}.
     *
     * @param rangosCajas se rellena con el rango de peso de las filas de CAJA
     *                    de cada bloque ("H22:H29"), sin las filas "PALET n":
     *                    es lo que suma el peso de los cartones
     * @return índice 0-based de la fila TOTAL tras el desplazamiento
     */
    private int escribirCuerpo(Sheet hoja, Map<Integer, List<CajaData>> porPalet, List<CajaData> sinPalet,
                               Map<Integer, String> medidasPorPalet, Map<Integer, Double> taraPorPalet,
                               DatosEnvio envio, List<String> rangosCajas) {
        Row filaPaletModelo = hoja.getRow(IDX_FILA_PALET_MODELO);
        Row filaDatoModelo = hoja.getRow(IDX_FILA_DATO_MODELO);
        CellStyle[] estiloPalet = capturarEstilos(filaPaletModelo);
        CellStyle[] estiloDato = capturarEstilos(filaDatoModelo);
        short alturaPalet = filaPaletModelo.getHeight();
        short alturaDato = filaDatoModelo.getHeight();

        int totalPalets = porPalet.size() + (sinPalet.isEmpty() ? 0 : 1);
        int totalCajas = porPalet.values().stream().mapToInt(List::size).sum() + sinPalet.size();
        int totalFilas = totalPalets + totalCajas;
        int filasDisponibles = IDX_FILA_TOTAL_MODELO - IDX_FILA_PALET_MODELO;
        int desplazamiento = Math.max(0, totalFilas - filasDisponibles);
        if (desplazamiento > 0) {
            hoja.shiftRows(IDX_FILA_TOTAL_MODELO, hoja.getLastRowNum(), desplazamiento);
        }

        String letraPeso = CellReference.convertNumToColString(COL_PESO);
        int idx = IDX_FILA_PALET_MODELO;
        for (Map.Entry<Integer, List<CajaData>> entrada : porPalet.entrySet()) {
            int inicio = idx;
            idx = escribirBloquePalet(hoja, idx, "PALET " + entrada.getKey(), medidasPorPalet.get(entrada.getKey()),
                    taraPorPalet.getOrDefault(entrada.getKey(), PaletData.TARA_DEFECTO_KG),
                    entrada.getValue(), envio, filaPaletModelo, filaDatoModelo, estiloPalet, estiloDato,
                    alturaPalet, alturaDato);
            anadirRangoCajas(rangosCajas, letraPeso, inicio, idx);
        }
        if (!sinPalet.isEmpty()) {
            int inicio = idx;
            idx = escribirBloquePalet(hoja, idx, "SIN PALET", null, 0, sinPalet, envio,
                    filaPaletModelo, filaDatoModelo, estiloPalet, estiloDato, alturaPalet, alturaDato);
            anadirRangoCajas(rangosCajas, letraPeso, inicio, idx);
        }
        return IDX_FILA_PALET_MODELO + totalFilas;
    }

    /**
     * El rango de las filas de caja de un bloque que empieza en la fila
     * 0-based {@code inicio} (la de "PALET n") y acaba justo antes de
     * {@code siguiente}. En 1-based las cajas van de inicio+2 a siguiente.
     */
    private static void anadirRangoCajas(List<String> rangos, String letra, int inicio, int siguiente) {
        if (siguiente - inicio > 1) {
            rangos.add(letra + (inicio + 2) + ":" + letra + siguiente);
        }
    }

    private int escribirBloquePalet(Sheet hoja, int idx, String etiqueta, String medidas, double taraKg,
                                    List<CajaData> cajas, DatosEnvio envio, Row filaPaletModelo, Row filaDatoModelo,
                                    CellStyle[] estiloPalet, CellStyle[] estiloDato, short alturaPalet, short alturaDato) {
        Row filaPalet = (idx == IDX_FILA_PALET_MODELO) ? filaPaletModelo
                : crearFilaConEstilo(hoja, idx, estiloPalet, alturaPalet);
        filaPalet.getCell(COL_BOX_NUM).setCellValue(etiqueta);
        if (taraKg > 0) {
            filaPalet.getCell(COL_PESO).setCellValue(taraKg);
        }
        if (medidas != null && !medidas.isBlank()) {
            filaPalet.getCell(COL_TAMANO).setCellValue(medidas.toUpperCase() + " cm");
        }
        idx++;

        for (CajaFisica cajaFisica : CajaFisica.agrupar(cajas)) {
            // El peso de la caja va solo en su primera fila: las demás
            // líneas de una caja mixta comparten ese peso, no suman.
            boolean primeraLinea = true;
            for (CajaData caja : cajaFisica.lineas()) {
                Row filaDato = (idx == IDX_FILA_DATO_MODELO) ? filaDatoModelo
                        : crearFilaConEstilo(hoja, idx, estiloDato, alturaDato);
                filaDato.getCell(COL_BOX_NUM).setCellValue(caja.getNumeroCaja());
                filaDato.getCell(COL_REFERENCIA).setCellValue(caja.getReferencia());
                if (caja.getModelo() != null) {
                    filaDato.getCell(COL_MODELO).setCellValue(caja.getModelo());
                }
                if (envio.getTemporada() != null) {
                    filaDato.getCell(COL_TEMPORADA).setCellValue(envio.getTemporada());
                }
                filaDato.getCell(COL_COLOR).setCellValue(caja.getCodigoColor());
                filaDato.getCell(COL_UNIDADES).setCellValue(caja.getCantidad());
                if (primeraLinea && cajaFisica.pesoBrutoKg() != null) {
                    filaDato.getCell(COL_PESO).setCellValue(cajaFisica.pesoBrutoKg());
                }
                filaDato.getCell(COL_TAMANO).setCellValue(caja.getTamanoCaja());
                primeraLinea = false;
                idx++;
            }
        }
        return idx;
    }

    private Row crearFilaConEstilo(Sheet hoja, int idx, CellStyle[] estilos, short altura) {
        Row fila = hoja.getRow(idx);
        if (fila == null) {
            fila = hoja.createRow(idx);
        }
        fila.setHeight(altura);
        for (int col = 0; col <= ULTIMA_COLUMNA; col++) {
            if (estilos[col] != null) {
                fila.createCell(col).setCellStyle(estilos[col]);
            }
        }
        return fila;
    }

    private CellStyle[] capturarEstilos(Row fila) {
        CellStyle[] estilos = new CellStyle[ULTIMA_COLUMNA + 1];
        for (int col = 0; col <= ULTIMA_COLUMNA; col++) {
            Cell celda = fila.getCell(col);
            estilos[col] = (celda != null) ? celda.getCellStyle() : null;
        }
        return estilos;
    }

    private void escribirTotalYResumen(Sheet hoja, int idxFilaTotal, DestinoData destino,
                                       List<String> rangosCajas) {
        Row filaTotal = hoja.getRow(idxFilaTotal);
        // Rango completo del cuerpo, filas "PALET n" incluidas: llevan su
        // tara en la columna de peso, así el TOTAL ya es cartones + palets
        // (misma convención que la fórmula (H23*16)+H21 del ejemplo real).
        int primeraFilaExcel = IDX_FILA_PALET_MODELO + 1; // 1-based, primer PALET
        int ultimaFilaExcel = idxFilaTotal; // 0-based == última fila de datos + 1 en 1-based
        String letraUnidades = CellReference.convertNumToColString(COL_UNIDADES);
        String letraPeso = CellReference.convertNumToColString(COL_PESO);

        filaTotal.getCell(COL_UNIDADES).setCellFormula(
                "SUM(" + letraUnidades + primeraFilaExcel + ":" + letraUnidades + ultimaFilaExcel + ")");
        filaTotal.getCell(COL_PESO).setCellFormula(
                "SUM(" + letraPeso + primeraFilaExcel + ":" + letraPeso + ultimaFilaExcel + ")");

        // Todo el resumen va por caja FÍSICA, no por línea: una caja mixta
        // (varias líneas con el mismo nº de caja) es un solo cartón, pesa
        // una sola vez (el peso de su línea líder, que es la única fila que
        // lo lleva escrito) y ocupa un volumen.
        List<CajaFisica> cajas = CajaFisica.agrupar(destino.getCajas());
        List<String> medidas = cajas.stream().map(c -> c.lider().getTamanoCaja()).toList();

        // Bloque SHIPMENT DETAILS (I12-I17): mismas filas para cualquier
        // número de bloques, no se desplazan (están por encima del cuerpo).
        // Pesos y volumen van como FÓRMULA y no como resultado, para que se
        // vea de dónde salen y se recalculen si alguien corrige una caja: el
        // peso de los cartones suma las filas de caja (sin las de palet), el
        // bruto es la fila TOTAL (que ya lleva las taras de las filas de
        // palet) y el volumen lleva las medidas, que en la hoja son texto.
        Map<String, CellStyle> estilos = new LinkedHashMap<>();
        formulaOCero(conFormato(celda(hoja, 11, 8), FORMATO_KG, estilos),         // TOTAL WEIGHT
                rangosCajas.isEmpty() ? null : rangosCajas.stream()
                        .map(rango -> "SUM(" + rango + ")")
                        .reduce((a, b) -> a + "+" + b).orElseThrow());
        conFormato(celda(hoja, 12, 8), FORMATO_KG, estilos)                       // TOTAL GROSS WEIGHT
                .setCellFormula(letraPeso + (idxFilaTotal + 1));
        formulaOCero(conFormato(celda(hoja, 13, 8), FORMATO_M3, estilos),         // TOTAL VOLUME
                VolumenUtil.formulaVolumenM3(medidas));
        celda(hoja, 15, 8).setCellValue(cajas.size());                       // TOTAL CARTONS
        celda(hoja, 16, 8).setCellValue(ResumenUtil.resumenConteo(medidas)); // CARTON DIMENTIONS
    }

    /** Sin nada que sumar, un cero: una fórmula vacía no es una fórmula. */
    private static void formulaOCero(Cell celda, String formula) {
        if (formula == null) {
            celda.setCellValue(0);
        } else {
            celda.setCellFormula(formula);
        }
    }

    /**
     * Le pone a la celda un formato de número con la unidad. El estilo de la
     * plantilla no se toca en sitio —lo comparten otras celdas de la hoja—:
     * se clona, uno por formato y estilo de origen.
     */
    private static Cell conFormato(Cell celda, String formato, Map<String, CellStyle> cache) {
        CellStyle original = celda.getCellStyle();
        Workbook libro = celda.getSheet().getWorkbook();
        celda.setCellStyle(cache.computeIfAbsent(formato + "|" + original.getIndex(), clave -> {
            CellStyle conUnidad = libro.createCellStyle();
            conUnidad.cloneStyleFrom(original);
            conUnidad.setDataFormat(libro.createDataFormat().getFormat(formato));
            return conUnidad;
        }));
        return celda;
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

package com.puntotres.packinglist.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.VolumenUtil;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.DestinoClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.CajaFisica;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

/**
 * Genera el packing list APC: UN excel por destino con todas sus
 * referencias (bolsos y cinturones comparten plantilla), las cajas
 * agrupadas por palet ("PALET n" + subtotal de peso con su tara), fila
 * TOTAL y las líneas de resumen del pie.
 *
 * Una caja física puede ocupar VARIAS filas (una por combinación de
 * talla/canal/color, como en el ejemplo real del cliente): el número de
 * caja (Nº COLIS) y el peso bruto de la caja entera solo se escriben en la
 * primera fila; las siguientes llevan el resto de columnas. Las entradas
 * del JSON con el mismo número de caja se agrupan aquí.
 *
 * La tara de cada palet viene del JSON (palets[].tara); si falta se asume
 * {@link #TARA_PALET_KG_DEFECTO} (10 kg). Para el GROSS VOLUME del pie cada
 * palet aporta {@link #VOLUMEN_PALET_M3_DEFECTO} (0.168 m3, la base del
 * palet, derivado del ejemplo real: sus "medidas" describen el palet
 * cargado y NO se usan para el volumen).
 */
@Service
public class ApcExcelBuilder implements GeneradorPackingListCliente {

    private static final String RUTA_PLANTILLA = "/client-packinglist/apc-packing-list-template.xlsx";
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Formato con el que APC escribe la fecha en F12 (texto, no fecha Excel). */
    private static final DateTimeFormatter FORMATO_FECHA_APC = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    /**
     * Rótulo del bloque de las cajas que van sueltas. En inglés, como el
     * resto de la plantilla: este excel lo lee el cliente, y un envío de
     * pocas cajas va sin palet de serie, así que ya no es un caso raro.
     */
    private static final String ETIQUETA_SIN_PALET = "NO PALLET";
    private static final double TARA_PALET_KG_DEFECTO = 10.0;
    private static final double VOLUMEN_PALET_M3_DEFECTO = 0.168;

    // Índices 0-based de fila de la plantilla limpia.
    private static final int IDX_FILA_PALET_MODELO = 16;  // fila 17
    private static final int IDX_FILA_CAJA_MODELO = 17;   // fila 18
    private static final int IDX_FILA_PESO_TOTAL = 18;    // fila 19
    private static final int IDX_FILA_TOTAL = 19;         // fila 20
    private static final int IDX_RESUMEN_PRIMERA = 21;    // fila 22 (6 líneas)

    // Índices 0-based de columna (fusiones B:C, D:E, G:H, I:J, K:L).
    private static final int COL_NUM_CAJA = 1;    // B Nº COLIS / etiqueta PALET
    private static final int COL_MODELO = 3;      // D MODÈLE
    private static final int COL_LIVRAISON = 5;   // F Livraison code
    private static final int COL_COMANDA = 6;     // G COMMANDE
    private static final int COL_REFERENCIA = 8;  // I RÉFÉRENCE
    private static final int COL_CANAL = 10;      // K DESTINATION (canal de la línea)
    private static final int COL_COLOR = 12;      // M COLORIS
    private static final int COL_TALLA = 13;      // N SIZE
    private static final int COL_PESO_BRUTO = 14; // O POIDS BRUT
    private static final int COL_CANTIDAD = 15;   // P QUANTITE
    private static final int ULTIMA_COLUMNA = COL_CANTIDAD;

    @Override
    public TipoPlantilla tipo() {
        return TipoPlantilla.APC;
    }

    @Override
    public List<ExcelGenerado> generar(DestinoData destino, List<PaletData> palets,
                                       DatosEnvio envio, ClienteConfig cliente) throws IOException {
        DestinoClienteConfig destinoConfig = cliente.destinoPara(destino.getNombreDestino())
                .orElseThrow(() -> new IllegalStateException(
                        "El cliente " + cliente.getNombre() + " no tiene datos configurados para el destino '"
                        + destino.getNombreDestino() + "'"));

        List<Bloque> bloques = agruparPorPalet(destino, palets);

        try (InputStream plantilla = abrirPlantilla();
             Workbook wb = new XSSFWorkbook(plantilla);
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            Sheet hoja = wb.getSheetAt(0);
            wb.setSheetName(0, nombreHoja(envio.getNumeroFactura(), destino.getNombreDestino()));

            escribirCabecera(hoja, destinoConfig, envio);
            ResultadoBloques resultado = escribirBloques(hoja, bloques);
            escribirTotales(hoja, resultado);
            escribirResumen(hoja, resultado, destino, bloques);

            wb.setForceFormulaRecalculation(true);
            wb.write(salida);

            // Pendiente = caja FÍSICA cuyo peso queda en blanco en el excel:
            // una entrada por nº de caja (no por línea) y solo el bruto, que
            // es lo único que escribe esta plantilla (no hay columna de neto).
            List<CajaData> pendientes = bloques.stream()
                    .flatMap(bloque -> bloque.cajas().stream())
                    .filter(caja -> caja.pesoBrutoKg() == null)
                    .map(CajaFisica::lider)
                    .toList();
            String nombreFichero = ("PKL_APC_" + destino.getNombreDestino() + "_"
                    + envio.getNumeroFactura() + ".xlsx").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
            return List.of(new ExcelGenerado(destino.getNombreDestino(), nombreFichero,
                    salida.toByteArray(), pendientes));
        }
    }

    private InputStream abrirPlantilla() {
        InputStream in = getClass().getResourceAsStream(RUTA_PLANTILLA);
        if (in == null) {
            throw new IllegalStateException("No se encuentra la plantilla en el classpath: " + RUTA_PLANTILLA);
        }
        return in;
    }

    private String nombreHoja(String factura, String destino) {
        String nombre = "APC INV " + factura + " " + destino;
        nombre = nombre.replaceAll("[\\\\/*?:\\[\\]]", "_");
        return nombre.length() > 31 ? nombre.substring(0, 31) : nombre;
    }

    private void escribirCabecera(Sheet hoja, DestinoClienteConfig destinoConfig, DatosEnvio envio) {
        celda(hoja, 7, 5).setCellValue(destinoConfig.getNombreCliente());   // F8
        celda(hoja, 9, 5).setCellValue(destinoConfig.getDireccion());       // F10
        // La fecha se escribe como texto dd.MM.yyyy, igual que el original del
        // cliente: F12 tiene formato General y un LocalDate se vería como el
        // número de serie de Excel (p. ej. "46222").
        celda(hoja, 11, 5).setCellValue(
                LocalDate.parse(envio.getFechaFactura(), FORMATO_FECHA)
                        .format(FORMATO_FECHA_APC)); // F12
        celda(hoja, 13, 15).setCellValue(envio.getNumeroFactura());         // P14
    }

    /**
     * Un palet y las cajas físicas que lleva encima. medidas es null en el
     * bloque de las cajas que van sueltas (que no es un palet) y también en
     * un palet cuyo JSON no las trae.
     */
    private record Bloque(String etiqueta, double taraKg, String medidas, List<CajaFisica> cajas) {
        int numeroDeFilas() {
            return 1 + cajas.stream().mapToInt(c -> c.lineas().size()).sum();
        }

        boolean esPalet() {
            return !ETIQUETA_SIN_PALET.equals(etiqueta);
        }
    }

    /** Fila 1-based de la primera y última fila de datos de un bloque. */
    private record RangoFilas(int primeraFilaExcel, int ultimaFilaExcel) {
    }

    private record ResultadoBloques(int idxFilaPesoTotal, int idxFilaTotal, List<RangoFilas> rangosPorBloque) {
    }

    private List<Bloque> agruparPorPalet(DestinoData destino, List<PaletData> palets) {
        Map<Integer, Double> taraPorPalet = new LinkedHashMap<>();
        Map<Integer, String> medidasPorPalet = new LinkedHashMap<>();
        for (PaletData palet : palets) {
            taraPorPalet.put(palet.getNumeroPalet(),
                    palet.getTara() != null ? palet.getTara() : TARA_PALET_KG_DEFECTO);
            medidasPorPalet.put(palet.getNumeroPalet(), palet.getMedidas());
        }

        Map<Integer, Map<Integer, List<CajaData>>> porPaletYCaja = new LinkedHashMap<>();
        Map<Integer, List<CajaData>> sinPaletPorCaja = new LinkedHashMap<>();
        for (CajaData caja : destino.getCajas()) {
            Map<Integer, List<CajaData>> porCaja = CajaData.vaSuelta(caja.getNumeroPalet())
                    ? sinPaletPorCaja
                    : porPaletYCaja.computeIfAbsent(caja.getNumeroPalet(), n -> new LinkedHashMap<>());
            porCaja.computeIfAbsent(caja.getNumeroCaja(), n -> new ArrayList<>()).add(caja);
        }

        List<Bloque> bloques = new ArrayList<>();
        porPaletYCaja.forEach((numeroPalet, porCaja) -> bloques.add(new Bloque(
                "PALET " + numeroPalet,
                taraPorPalet.getOrDefault(numeroPalet, TARA_PALET_KG_DEFECTO),
                medidasPorPalet.get(numeroPalet),
                aCajasFisicas(porCaja))));
        if (!sinPaletPorCaja.isEmpty()) {
            // Sin palet físico no hay tara que sumar.
            bloques.add(new Bloque(ETIQUETA_SIN_PALET, 0, null, aCajasFisicas(sinPaletPorCaja)));
        }
        return bloques;
    }

    private static List<CajaFisica> aCajasFisicas(Map<Integer, List<CajaData>> porCaja) {
        List<CajaFisica> cajas = new ArrayList<>();
        porCaja.forEach((numero, lineas) -> cajas.add(new CajaFisica(numero, lineas)));
        return cajas;
    }

    /**
     * Escribe los bloques (fila de palet + una fila por LÍNEA de caja) a
     * partir de las filas modelo de la plantilla, desplazando lo que hay
     * debajo (totales y resumen). Las filas nuevas clonan estilo y fusiones
     * (B:C, D:E, G:H, I:J, K:L) de la fila modelo de caja, porque
     * {@code createRow} no copia ninguna de las dos cosas.
     */
    private ResultadoBloques escribirBloques(Sheet hoja, List<Bloque> bloques) {
        Row filaPaletModelo = hoja.getRow(IDX_FILA_PALET_MODELO);
        Row filaCajaModelo = hoja.getRow(IDX_FILA_CAJA_MODELO);
        CellStyle[] estiloPalet = capturarEstilos(filaPaletModelo);
        CellStyle[] estiloCaja = capturarEstilos(filaCajaModelo);
        short alturaPalet = filaPaletModelo.getHeight();
        short alturaCaja = filaCajaModelo.getHeight();

        int totalFilas = bloques.stream().mapToInt(Bloque::numeroDeFilas).sum();
        int filasDisponibles = IDX_FILA_PESO_TOTAL - IDX_FILA_PALET_MODELO;
        int desplazamiento = Math.max(0, totalFilas - filasDisponibles);
        if (desplazamiento > 0) {
            hoja.shiftRows(IDX_FILA_PESO_TOTAL, hoja.getLastRowNum(), desplazamiento);
        }

        List<RangoFilas> rangos = new ArrayList<>();
        int idx = IDX_FILA_PALET_MODELO;
        for (Bloque bloque : bloques) {
            Row filaPalet = (idx == IDX_FILA_PALET_MODELO)
                    ? filaPaletModelo : crearFilaConEstilo(hoja, idx, estiloPalet, alturaPalet, false);
            idx++;

            int primeraFilaExcel = idx + 1; // 1-based
            for (CajaFisica caja : bloque.cajas()) {
                boolean primeraLinea = true;
                for (CajaData linea : caja.lineas()) {
                    Row fila = (idx == IDX_FILA_CAJA_MODELO)
                            ? filaCajaModelo // ya trae estilo y fusiones de la plantilla
                            : crearFilaConEstilo(hoja, idx, estiloCaja, alturaCaja, true);
                    escribirLinea(fila, caja, linea, primeraLinea);
                    primeraLinea = false;
                    idx++;
                }
            }
            int ultimaFilaExcel = idx; // 1-based: idx ya apunta a la fila siguiente
            rangos.add(bloque.cajas().isEmpty() ? null : new RangoFilas(primeraFilaExcel, ultimaFilaExcel));
            escribirFilaPalet(filaPalet, bloque, primeraFilaExcel, ultimaFilaExcel);
        }
        return new ResultadoBloques(IDX_FILA_PESO_TOTAL + desplazamiento,
                IDX_FILA_TOTAL + desplazamiento, rangos);
    }

    private Row crearFilaConEstilo(Sheet hoja, int idx, CellStyle[] estilos, short altura, boolean conFusiones) {
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
        if (conFusiones) {
            for (int col : new int[] {COL_NUM_CAJA, COL_MODELO, COL_COMANDA, COL_REFERENCIA, COL_CANAL}) {
                hoja.addMergedRegion(new CellRangeAddress(idx, idx, col, col + 1));
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

    private void escribirFilaPalet(Row fila, Bloque bloque, int primeraFilaExcel, int ultimaFilaExcel) {
        fila.getCell(COL_NUM_CAJA).setCellValue(bloque.etiqueta());
        if (bloque.cajas().isEmpty()) {
            fila.getCell(COL_PESO_BRUTO).setCellValue(bloque.taraKg());
            return;
        }
        String letraPeso = CellReference.convertNumToColString(COL_PESO_BRUTO);
        String suma = "SUM(" + letraPeso + primeraFilaExcel + ":" + letraPeso + ultimaFilaExcel + ")";
        fila.getCell(COL_PESO_BRUTO).setCellFormula(
                bloque.taraKg() > 0 ? suma + "+" + numeroParaFormula(bloque.taraKg()) : suma);
    }

    /**
     * El Nº COLIS y el peso bruto (de la caja ENTERA, el de su línea
     * líder) van solo en la primera línea de cada caja física, como en el
     * ejemplo del cliente.
     */
    private void escribirLinea(Row fila, CajaFisica caja, CajaData linea, boolean primeraLinea) {
        if (primeraLinea) {
            fila.getCell(COL_NUM_CAJA).setCellValue(caja.numeroCaja());
            Double peso = caja.pesoBrutoKg();
            if (peso != null) {
                fila.getCell(COL_PESO_BRUTO).setCellValue(peso);
            }
        }
        if (linea.getModelo() != null) {
            fila.getCell(COL_MODELO).setCellValue(linea.getModelo());
        }
        if (linea.getLivraisonCode() != null) {
            fila.getCell(COL_LIVRAISON).setCellValue(linea.getLivraisonCode());
        }
        if (linea.getNumeroPedido() != null) {
            fila.getCell(COL_COMANDA).setCellValue(linea.getNumeroPedido());
        }
        fila.getCell(COL_REFERENCIA).setCellValue(linea.getReferencia());
        if (linea.getCanal() != null) {
            fila.getCell(COL_CANAL).setCellValue(linea.getCanal());
        }
        fila.getCell(COL_COLOR).setCellValue(linea.getCodigoColor());
        if (linea.getTalla() != null) {
            fila.getCell(COL_TALLA).setCellValue(linea.getTalla());
        }
        fila.getCell(COL_CANTIDAD).setCellValue(linea.getCantidad());
    }

    /**
     * Fila de total de peso (suma por bloques, SIN las filas "PALET n" que
     * ya son subtotales) y fila TOTAL con el total de unidades (rango
     * completo: las filas de palet no llevan cantidad).
     */
    private void escribirTotales(Sheet hoja, ResultadoBloques resultado) {
        String letraPeso = CellReference.convertNumToColString(COL_PESO_BRUTO);
        String letraCantidad = CellReference.convertNumToColString(COL_CANTIDAD);

        String formulaPeso = resultado.rangosPorBloque().stream()
                .filter(r -> r != null)
                .map(r -> "SUM(" + letraPeso + r.primeraFilaExcel() + ":" + letraPeso + r.ultimaFilaExcel() + ")")
                .reduce((a, b) -> a + "+" + b).orElse("0");
        celda(hoja, resultado.idxFilaPesoTotal(), COL_PESO_BRUTO).setCellFormula(formulaPeso);

        int primeraFilaExcel = IDX_FILA_PALET_MODELO + 2;
        int ultimaFilaExcel = resultado.idxFilaPesoTotal(); // 1-based última fila de datos
        celda(hoja, resultado.idxFilaTotal(), COL_CANTIDAD).setCellFormula(
                "SUM(" + letraCantidad + primeraFilaExcel + ":" + letraCantidad + ultimaFilaExcel + ")");
    }

    /**
     * Las 6 líneas del pie, en el formato del ejemplo real del cliente
     * (docs/Packing Lists/apc-bags-and-belts-complete-example.xlsx): rótulo
     * en la columna D y valor en la E, y los pesos y volúmenes como NÚMEROS,
     * no como texto con las unidades pegadas. Antes eran 5 frases enteras
     * metidas en la D.
     *
     * "GROSS" es siempre cartones + palets, en peso igual que en volumen: un
     * envío que va suelto (sin palets) tiene el mismo peso bruto que el de
     * sus cartones y ninguna tara inventada de por medio.
     */
    private void escribirResumen(Sheet hoja, ResultadoBloques resultado, DestinoData destino,
                                 List<Bloque> bloques) {
        int desplazamiento = resultado.idxFilaTotal() - IDX_FILA_TOTAL;
        int idx = IDX_RESUMEN_PRIMERA + desplazamiento;

        // El peso se cuenta UNA vez por caja física (el de su línea líder),
        // no por línea: una caja mixta no pesa más por tener varias tallas.
        double pesoCartones = bloques.stream()
                .flatMap(bloque -> bloque.cajas().stream())
                .filter(caja -> caja.pesoBrutoKg() != null)
                .mapToDouble(CajaFisica::pesoBrutoKg).sum();
        double taras = bloques.stream().mapToDouble(Bloque::taraKg).sum();
        List<String> medidasCajasFisicas = medidasPorCajaFisica(destino);
        double volumenCartones = VolumenUtil.volumenTotalM3(medidasCajasFisicas);
        List<String> medidasPalets = bloques.stream().filter(Bloque::esPalet)
                .map(Bloque::medidas).toList();
        double volumenPalets = medidasPalets.size() * VOLUMEN_PALET_M3_DEFECTO;

        resumen(hoja, idx, "PALLETS").setCellValue(recuento(medidasPalets, ""));
        resumen(hoja, idx + 1, "CARTONS").setCellValue(recuento(medidasCajasFisicas, "cm"));
        resumen(hoja, idx + 2, "CARTON WEIGHT").setCellValue(redondear(pesoCartones, 2));
        resumen(hoja, idx + 3, "CARTONS VOLUME").setCellValue(redondear(volumenCartones, 3));
        resumen(hoja, idx + 4, "GROSS WEIGHT").setCellValue(redondear(pesoCartones + taras, 2));
        resumen(hoja, idx + 5, "GROSS VOLUME")
                .setCellValue(redondear(volumenCartones + volumenPalets, 3));
    }

    /** Escribe el rótulo en la D y devuelve la celda de valor (la E). */
    private Cell resumen(Sheet hoja, int idxFila, String rotulo) {
        celda(hoja, idxFila, 3).setCellValue(rotulo);
        return celda(hoja, idxFila, 4);
    }

    /** Una medida por caja FÍSICA (no por línea), para contar y sumar volumen. */
    private List<String> medidasPorCajaFisica(DestinoData destino) {
        return CajaFisica.agrupar(destino.getCajas()).stream()
                .map(caja -> caja.lider().getTamanoCaja())
                .toList();
    }

    /**
     * "53 (60x40x40cm)" cuando todos miden lo mismo; con varias medidas,
     * "53 (2*60x40x30cm+51*60x40x40cm)". Sin nada que contar, solo "0": un
     * paréntesis vacío parecería un dato que falta.
     *
     * El sufijo lo pone quien llama porque el ejemplo del cliente lo lleva en
     * los cartones ("60x40x40cm") y no en los palets ("80x120x170").
     */
    private static String recuento(List<String> medidas, String sufijo) {
        if (medidas.isEmpty()) {
            return "0";
        }
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (String medida : medidas) {
            // Una medida que falta se rotula "?": el bulto se cuenta igual
            // (existe), pero la celda no puede decir "null".
            conteo.merge(VolumenUtil.etiqueta(medida), 1, Integer::sum);
        }
        String desglose = conteo.size() == 1
                ? conteo.keySet().iterator().next() + sufijo
                : conteo.entrySet().stream()
                        .map(e -> e.getValue() + "*" + e.getKey() + sufijo)
                        .reduce((a, b) -> a + "+" + b).orElse("");
        return medidas.size() + " (" + desglose + ")";
    }

    /**
     * El pie lleva NÚMEROS, no texto con las unidades pegadas, así que hay
     * que redondear aquí: sin esto la suma de decimales en coma flotante
     * escribiría "378,6400000000001" en una celda que lee el cliente.
     */
    private static double redondear(double valor, int decimales) {
        return BigDecimal.valueOf(valor)
                .setScale(decimales, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** 10.0 -> "10", 8.04 -> "8.04" (para incrustar en fórmulas). */
    private static String numeroParaFormula(double valor) {
        return BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString();
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

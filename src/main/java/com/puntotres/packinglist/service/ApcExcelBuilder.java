package com.puntotres.packinglist.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
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
 * La tara de cada palet es la que se teclea en la columna "Palet (Kg)" de
 * la revisión (o la del JSON, palets[].tara); si no hay ninguna se asume
 * {@link PaletData#TARA_DEFECTO_KG}. Para el GROSS VOLUME del pie cada
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

    // Las 6 líneas del pie: rótulo en la D y valor en la E.
    private static final int COL_ROTULO_PIE = 3;  // D
    private static final int COL_VALOR_PIE = 4;   // E

    /**
     * Ancho de la columna D (MODÈLE, y también el rótulo de las 6 líneas del
     * pie) en unidades de POI: 1/256 de carácter, así que 19 caracteres. Es
     * un ajuste del área de impresión hecho viendo el papel, no algo que se
     * deduzca de la plantilla, que venía con 14,38.
     */
    private static final int ANCHO_COL_MODELO = 19 * 256;

    /**
     * Ancho de la columna E (la mitad derecha de MODÈLE, que va combinada
     * D:E, y el valor de las 6 líneas del pie): 8 caracteres. El mismo tipo
     * de ajuste que la D, pedido por el cliente viendo el papel; la
     * plantilla venía con 11,63, y con 6 un peso de más de cinco cifras ya
     * no cabía en el pie.
     */
    private static final int ANCHO_COL_VALOR_PIE = 8 * 256;

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
        // Cada destinación lleva su factura: la tecleada en su cabecera de la
        // revisión o, si no hay, la del envío.
        String factura = envio.facturaPara(destino);

        try (InputStream plantilla = abrirPlantilla();
             Workbook wb = new XSSFWorkbook(plantilla);
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            Sheet hoja = wb.getSheetAt(0);
            wb.setSheetName(0, nombreHoja(factura, destino.getNombreDestino()));
            hoja.setColumnWidth(COL_MODELO, ANCHO_COL_MODELO);
            hoja.setColumnWidth(COL_VALOR_PIE, ANCHO_COL_VALOR_PIE);

            escribirCabecera(hoja, destinoConfig, envio, factura);
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
            String nombreFichero = ExcelGenerado.nombreXlsx("PKL_APC",
                    destino.getNombreDestino(), factura);
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

    /** "APC INV FA-1 WHOLESALE", o "APC INV WHOLESALE" si la destinación va sin factura. */
    private String nombreHoja(String factura, String destino) {
        String nombre = factura == null || factura.isBlank()
                ? "APC INV " + destino
                : "APC INV " + factura.trim() + " " + destino;
        nombre = nombre.replaceAll("[\\\\/*?:\\[\\]]", "_");
        return nombre.length() > 31 ? nombre.substring(0, 31) : nombre;
    }

    private void escribirCabecera(Sheet hoja, DestinoClienteConfig destinoConfig, DatosEnvio envio,
                                  String factura) {
        celda(hoja, 7, 5).setCellValue(destinoConfig.getNombreCliente());   // F8
        celda(hoja, 9, 5).setCellValue(destinoConfig.getDireccion());       // F10
        // La fecha se escribe como texto dd.MM.yyyy, igual que el original del
        // cliente: F12 tiene formato General y un LocalDate se vería como el
        // número de serie de Excel (p. ej. "46222").
        celda(hoja, 11, 5).setCellValue(
                LocalDate.parse(envio.getFechaFactura(), FORMATO_FECHA)
                        .format(FORMATO_FECHA_APC)); // F12
        celda(hoja, 13, 15).setCellValue(factura);                          // P14
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

    /**
     * filasCabeceraBloque: fila 1-based de la fila "PALET n" (o "NO PALLET")
     * de cada bloque, que lleva el peso de sus cajas MÁS la tara del palet.
     */
    private record ResultadoBloques(int idxFilaPesoTotal, int idxFilaTotal, List<RangoFilas> rangosPorBloque,
                                    List<Integer> filasCabeceraBloque) {
    }

    private List<Bloque> agruparPorPalet(DestinoData destino, List<PaletData> palets) {
        Map<Integer, Double> taraPorPalet = new LinkedHashMap<>();
        Map<Integer, String> medidasPorPalet = new LinkedHashMap<>();
        for (PaletData palet : palets) {
            taraPorPalet.put(palet.getNumeroPalet(), palet.taraOPorDefecto());
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
                taraPorPalet.getOrDefault(numeroPalet, PaletData.TARA_DEFECTO_KG),
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
        List<Integer> filasCabecera = new ArrayList<>();
        int idx = IDX_FILA_PALET_MODELO;
        for (Bloque bloque : bloques) {
            Row filaPalet = (idx == IDX_FILA_PALET_MODELO)
                    ? filaPaletModelo : crearFilaConEstilo(hoja, idx, estiloPalet, alturaPalet, false);
            filasCabecera.add(idx + 1); // 1-based
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
                IDX_FILA_TOTAL + desplazamiento, rangos, filasCabecera);
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
        // El nombre del modelo va siempre en mayúsculas, venga de la
        // Désignation del pedido ("le neige") o de la entrada.
        if (linea.getModelo() != null) {
            fila.getCell(COL_MODELO).setCellValue(linea.getModelo().trim().toUpperCase(Locale.ROOT));
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
     * en la columna D y valor en la E. Antes eran 5 frases enteras metidas
     * en la D.
     *
     * <p>Los pesos y volúmenes son FÓRMULAS, no el resultado: quien abre el
     * excel ve de dónde sale cada número y, si corrige el peso de una caja o
     * la tara de un palet, el pie se recalcula solo. Donde el número está en
     * una celda de la hoja la fórmula apunta a esa celda; donde no, lleva los
     * números a la vista:
     * <ul>
     * <li>CARTON WEIGHT apunta a la fila de total de peso, que ya suma solo
     *     las cajas (una vez por caja física, el peso de su línea líder).</li>
     * <li>CARTONS VOLUME lleva las medidas, porque esta plantilla no tiene
     *     columna de medida de caja a la que apuntar
     *     ({@link VolumenUtil#formulaVolumenM3}).</li>
     * <li>GROSS WEIGHT suma las filas "PALET n": cada una ya es sus cajas más
     *     su tara, y la de "NO PALLET" sus cajas solas.</li>
     * <li>GROSS VOLUME es CARTONS VOLUME más la base de cada palet.</li>
     * </ul>
     *
     * <p>"GROSS" es siempre cartones + palets, en peso igual que en volumen:
     * un envío que va suelto (sin palets) tiene el mismo peso bruto que el de
     * sus cartones y ninguna tara inventada de por medio.
     */
    private void escribirResumen(Sheet hoja, ResultadoBloques resultado, DestinoData destino,
                                 List<Bloque> bloques) {
        int desplazamiento = resultado.idxFilaTotal() - IDX_FILA_TOTAL;
        int idx = IDX_RESUMEN_PRIMERA + desplazamiento;
        String letraPeso = CellReference.convertNumToColString(COL_PESO_BRUTO);
        String letraValor = CellReference.convertNumToColString(COL_VALOR_PIE);

        List<String> medidasCajasFisicas = medidasPorCajaFisica(destino);
        List<String> medidasPalets = bloques.stream().filter(Bloque::esPalet)
                .map(Bloque::medidas).toList();

        Map<Integer, CellStyle> estilos = new LinkedHashMap<>();
        resumen(hoja, idx, "PALLETS", estilos).setCellValue(recuento(medidasPalets, ""));
        resumen(hoja, idx + 1, "CARTONS", estilos)
                .setCellValue(recuento(medidasCajasFisicas, "cm"));
        resumen(hoja, idx + 2, "CARTON WEIGHT", estilos)
                .setCellFormula(letraPeso + (resultado.idxFilaPesoTotal() + 1));
        formulaOCero(resumen(hoja, idx + 3, "CARTONS VOLUME", estilos),
                VolumenUtil.formulaVolumenM3(medidasCajasFisicas));
        formulaOCero(resumen(hoja, idx + 4, "GROSS WEIGHT", estilos),
                resultado.filasCabeceraBloque().stream()
                        .map(fila -> letraPeso + fila)
                        .reduce((a, b) -> a + "+" + b).orElse(null));
        String volumenCartones = letraValor + (idx + 3 + 1);
        resumen(hoja, idx + 5, "GROSS VOLUME", estilos).setCellFormula(medidasPalets.isEmpty()
                ? volumenCartones
                : volumenCartones + "+" + medidasPalets.size() + "*"
                        + numeroParaFormula(VOLUMEN_PALET_M3_DEFECTO));
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
     * Escribe el rótulo en la D y devuelve la celda de valor (la E), ya
     * alineada a la izquierda.
     */
    private Cell resumen(Sheet hoja, int idxFila, String rotulo, Map<Integer, CellStyle> estilos) {
        celda(hoja, idxFila, COL_ROTULO_PIE).setCellValue(rotulo);
        return alinearIzquierda(celda(hoja, idxFila, COL_VALOR_PIE), estilos);
    }

    /**
     * Las seis celdas de valor del pie van alineadas a la izquierda, pegadas
     * a su rótulo de la D. Sin esto no quedan ni siquiera entre ellas: cuatro
     * llevan un NÚMERO y con la alineación General de la plantilla se van al
     * borde derecho de la celda, mientras que PALLETS y CARTONS, que son
     * texto, se quedan a la izquierda.
     *
     * <p>El estilo NO se toca en sitio: es el de la plantilla y lo comparten
     * muchas más celdas de la hoja, así que se clona (y se cachea por estilo
     * de origen, que en la plantilla son dos distintos entre las seis filas).
     */
    private static Cell alinearIzquierda(Cell celda, Map<Integer, CellStyle> cache) {
        CellStyle original = celda.getCellStyle();
        if (original.getAlignment() == HorizontalAlignment.LEFT) {
            return celda;
        }
        celda.setCellStyle(cache.computeIfAbsent((int) original.getIndex(), indice -> {
            CellStyle alineado = celda.getSheet().getWorkbook().createCellStyle();
            alineado.cloneStyleFrom(original);
            alineado.setAlignment(HorizontalAlignment.LEFT);
            return alineado;
        }));
        return celda;
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

package com.puntotres.packinglist.service.taller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Lee la hoja "LISTE DE COLIS" del packing list que manda el taller.
 *
 * Ese fichero no es una tabla limpia: encima lleva el membrete del taller y
 * una leyenda de campos, debajo los totales de bultos y peso, y la cabecera
 * cae donde caiga —en el ejemplar real, en la fila 9—. Por eso aquí no hay
 * ninguna coordenada fija: se busca la hoja por nombre normalizado, la
 * cabecera por sus títulos dentro de las primeras filas, y cada columna por
 * su nombre o por uno de sus sinónimos.
 *
 * Manda la CABECERA, nunca lo que haya escrito debajo. Es tentador deducir la
 * columna del contenido cuando un título no parece cuadrar con sus datos, pero
 * eso da un packing con números plausibles y equivocados, y nadie lo detecta
 * hasta el almacén. Si un taller titula mal una columna, la respuesta es un
 * sinónimo más en {@code Columna}, no una heurística.
 *
 * Los títulos vienen como vienen: con acentos, con la "o" voladita de "Nº", y
 * partidos en dos líneas dentro de la celda cuando no caben en el ancho de
 * columna ("QTITE /\nCOLIS" en el fichero real). De eso se encarga
 * {@link #normalizar}.
 *
 * Falta una columna obligatoria: se lanza, nombrando cuáles faltan y cuáles
 * se han leído. Falta una opcional: aviso, y el dato queda pendiente. Es la
 * única lectura del proyecto que puede impedir seguir, y lo hace porque sin
 * referencia o sin cantidad no hay packing que generar.
 */
public final class TallerColisExcel {

    /** Nombre (ya normalizado) de la hoja que trae el packing list. */
    public static final String HOJA_COLIS = "LISTE DE COLIS";

    private static final int MAX_FILAS_CABECERA = 20;
    private static final int FILAS_VACIAS_PARA_PARAR = 5;
    private static final String TALLA_UNICA = "U";

    /**
     * Las columnas que el lector conoce, con sus sinónimos ya normalizados.
     * Los acentos y los signos los quita {@link #normalizar}, así que
     * "QTÉ / COLIS", "N° DE COLIS" y "RÉFÉRENCE" no necesitan entrada propia.
     */
    private enum Columna {
        CLIENT(true, "CLIENT", "CLIENTE"),
        MOTIF(true, "MOTIF", "MOTIVO"),
        REFERENCE(true, "REFERENCE", "REF"),
        COULEUR(true, "COULEUR", "COLORIS", "COLOR"),
        TAILLE(false, "TAILLE", "SIZE", "TALLA"),
        DESTINATION(false, "DESTINATION", "DESTINO"),
        CODE(false, "CODE", "CODIGO"),
        NUM_EXPEDICION(false, "N EXPEDITION PUNTOTRES", "N EXPEDICION PUNTOTRES",
                "EXPEDITION PUNTOTRES"),
        QTITE_COLIS(false, "QTITE/COLIS", "QTE/COLIS", "QTITE COLIS", "QTE COLIS"),
        QUANTITE(true, "QUANTITE", "QUANTITE TOTALE", "QTE TOTALE"),
        NUM_COLIS(false, "N DE COLIS", "NO DE COLIS", "COLIS");

        private final boolean obligatoria;
        private final List<String> nombres;

        Columna(boolean obligatoria, String... nombres) {
            this.obligatoria = obligatoria;
            this.nombres = Arrays.asList(nombres);
        }

        boolean casa(String cabeceraNormalizada) {
            return nombres.contains(cabeceraNormalizada);
        }

        /** El nombre principal, el que se enseña en errores y avisos. */
        String rotulo() {
            return nombres.get(0);
        }
    }

    private final List<LineaTaller> lineas;
    private final List<String> avisos;

    private TallerColisExcel(List<LineaTaller> lineas, List<String> avisos) {
        this.lineas = List.copyOf(lineas);
        this.avisos = List.copyOf(avisos);
    }

    public static TallerColisExcel desdeBytes(byte[] contenido)
            throws IOException, TallerExcelException {
        return desdeBytes(contenido, null);
    }

    /**
     * @param nombreHoja hoja elegida a mano por el usuario cuando el libro no
     *                   trae ninguna llamada "LISTE DE COLIS"; null para
     *                   buscarla por nombre.
     */
    public static TallerColisExcel desdeBytes(byte[] contenido, String nombreHoja)
            throws IOException, TallerExcelException {
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            Sheet hoja = localizarHoja(libro, nombreHoja);
            List<String> avisos = new ArrayList<>();
            Map<Columna, Integer> columnas = localizarColumnas(hoja, avisos);
            return new TallerColisExcel(leerFilas(hoja, columnas, avisos), avisos);
        }
    }

    public List<LineaTaller> lineas() {
        return lineas;
    }

    /** Columnas opcionales que no estaban y datos que no se han podido leer. */
    public List<String> avisos() {
        return avisos;
    }

    // --- Localización ---

    private static Sheet localizarHoja(Workbook libro, String nombreElegido)
            throws HojaNoEncontradaException {
        String buscada = normalizar(nombreElegido == null ? HOJA_COLIS : nombreElegido);
        List<String> encontradas = new ArrayList<>();
        for (int i = 0; i < libro.getNumberOfSheets(); i++) {
            Sheet hoja = libro.getSheetAt(i);
            encontradas.add(hoja.getSheetName());
            if (normalizar(hoja.getSheetName()).equals(buscada)) {
                return hoja;
            }
        }
        throw new HojaNoEncontradaException(nombreElegido == null ? HOJA_COLIS : nombreElegido,
                encontradas);
    }

    /**
     * La cabecera es la PRIMERA fila de las 20 primeras que traiga todas las
     * columnas obligatorias. Si ninguna las trae, se lanza señalando la que
     * más cerca estuvo: es la que el usuario tiene que mirar.
     */
    private static Map<Columna, Integer> localizarColumnas(Sheet hoja, List<String> avisos)
            throws ColumnasAusentesException {
        Map<Columna, Integer> mejor = Map.of();
        List<String> mejoresCabeceras = List.of();
        int mejorFila = -1;

        int ultima = Math.min(hoja.getLastRowNum(), MAX_FILAS_CABECERA - 1);
        for (int f = hoja.getFirstRowNum(); f <= ultima; f++) {
            Row fila = hoja.getRow(f);
            if (fila == null) {
                continue;
            }
            Map<Columna, Integer> encontradas = columnasDe(hoja, fila);
            if (encontradas.keySet().containsAll(obligatorias())) {
                avisarDeLasOpcionalesQueFaltan(encontradas, avisos);
                return encontradas;
            }
            if (encontradas.size() > mejor.size()) {
                mejor = encontradas;
                mejoresCabeceras = cabecerasDe(hoja, fila);
                mejorFila = f + 1;
            }
        }
        Map<Columna, Integer> masCercana = mejor;
        List<String> ausentes = obligatorias().stream()
                .filter(columna -> !masCercana.containsKey(columna))
                .map(Columna::rotulo)
                .toList();
        throw new ColumnasAusentesException(ausentes, mejoresCabeceras, mejorFila);
    }

    private static Map<Columna, Integer> columnasDe(Sheet hoja, Row fila) {
        Map<Columna, Integer> encontradas = new LinkedHashMap<>();
        for (int c = 0; c < fila.getLastCellNum(); c++) {
            String cabecera = normalizar(textoDeCabecera(hoja, fila, c));
            if (cabecera.isEmpty()) {
                continue;
            }
            for (Columna columna : Columna.values()) {
                if (columna.casa(cabecera)) {
                    encontradas.putIfAbsent(columna, c);
                }
            }
        }
        return encontradas;
    }

    private static List<String> cabecerasDe(Sheet hoja, Row fila) {
        List<String> cabeceras = new ArrayList<>();
        for (int c = 0; c < fila.getLastCellNum(); c++) {
            String texto = textoDeCabecera(hoja, fila, c);
            if (!texto.isBlank()) {
                cabeceras.add(texto.trim());
            }
        }
        return cabeceras;
    }

    private static void avisarDeLasOpcionalesQueFaltan(Map<Columna, Integer> encontradas,
                                                       List<String> avisos) {
        for (Columna columna : Columna.values()) {
            if (columna.obligatoria || encontradas.containsKey(columna)) {
                continue;
            }
            avisos.add("La hoja del taller no tiene la columna '" + columna.rotulo() + "': "
                    + explicacionDe(columna));
        }
    }

    private static String explicacionDe(Columna columna) {
        return switch (columna) {
            case TAILLE -> "todas las tallas se toman como talla única";
            case QTITE_COLIS -> "habrá que decir cuántas unidades entran en cada caja";
            case CODE -> "en APC no se podrá saber a qué pedido y a qué destinación va cada fila";
            case DESTINATION -> "no se podrá contrastar la destinación con la del pedido";
            default -> "ese dato no llega y se resuelve en la pantalla siguiente";
        };
    }

    // --- Lectura ---

    private static List<LineaTaller> leerFilas(Sheet hoja, Map<Columna, Integer> columnas,
                                               List<String> avisos) {
        List<LineaTaller> lineas = new ArrayList<>();
        int primeraFilaDatos = filaDeCabecera(hoja, columnas) + 1;
        int vaciasSeguidas = 0;

        for (int f = primeraFilaDatos; f <= hoja.getLastRowNum(); f++) {
            Row fila = hoja.getRow(f);
            String referencia = texto(fila, columnas.get(Columna.REFERENCE));
            String cantidadCruda = texto(fila, columnas.get(Columna.QUANTITE));

            // Fila vacía = sin referencia y sin cantidad. Con este criterio los
            // totales de abajo ("Soit : 40 colis", "Poids Brut") también cuentan
            // como vacías, que es lo que se quiere: no son artículos.
            if (referencia.isBlank() && cantidadCruda.isBlank()) {
                if (++vaciasSeguidas >= FILAS_VACIAS_PARA_PARAR) {
                    break;
                }
                continue;
            }
            vaciasSeguidas = 0;
            lineas.add(leerLinea(fila, f, columnas, referencia, cantidadCruda, avisos));
        }
        return lineas;
    }

    private static LineaTaller leerLinea(Row fila, int indiceFila, Map<Columna, Integer> columnas,
                                         String referencia, String cantidadCruda,
                                         List<String> avisos) {
        return new LineaTaller(
                indiceFila + 1,
                mayusculas(texto(fila, columnas.get(Columna.CLIENT))),
                mayusculas(texto(fila, columnas.get(Columna.MOTIF))),
                mayusculas(referencia),
                mayusculas(texto(fila, columnas.get(Columna.COULEUR))),
                talla(texto(fila, columnas.get(Columna.TAILLE))),
                mayusculas(texto(fila, columnas.get(Columna.DESTINATION))),
                mayusculas(texto(fila, columnas.get(Columna.CODE))),
                mayusculas(texto(fila, columnas.get(Columna.NUM_EXPEDICION))),
                enteroOpcional(texto(fila, columnas.get(Columna.QTITE_COLIS))),
                cantidad(cantidadCruda, referencia, indiceFila + 1, avisos));
    }

    /**
     * La talla del papel: numérica se conserva (cinturones), cualquier otra
     * cosa —vacía, "TU", "U"— es talla única, que es lo que llevan los bolsos.
     */
    private static String talla(String crudo) {
        String limpio = crudo.trim();
        return limpio.matches("\\d+") ? limpio : TALLA_UNICA;
    }

    private static int cantidad(String crudo, String referencia, int fila, List<String> avisos) {
        Integer valor = enteroOpcional(crudo);
        if (valor != null) {
            return valor;
        }
        if (!crudo.isBlank()) {
            avisos.add("En la fila " + fila + " la cantidad de '" + mayusculas(referencia)
                    + "' no se entiende ('" + crudo.trim() + "'): se deja en 0 y hay que teclearla");
        }
        return 0;
    }

    private static Integer enteroOpcional(String crudo) {
        String limpio = crudo.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(limpio).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private static int filaDeCabecera(Sheet hoja, Map<Columna, Integer> columnas) {
        int ultima = Math.min(hoja.getLastRowNum(), MAX_FILAS_CABECERA - 1);
        for (int f = hoja.getFirstRowNum(); f <= ultima; f++) {
            Row fila = hoja.getRow(f);
            if (fila != null && columnasDe(hoja, fila).keySet().containsAll(obligatorias())) {
                // Si la cabecera está combinada verticalmente, la mitad de
                // abajo es la misma cabecera y no un dato.
                return ultimaFilaCombinada(hoja, f, columnas.get(Columna.REFERENCE));
            }
        }
        return hoja.getFirstRowNum();
    }

    private static int ultimaFilaCombinada(Sheet hoja, int fila, int columna) {
        for (CellRangeAddress region : hoja.getMergedRegions()) {
            if (region.isInRange(fila, columna)) {
                return region.getLastRow();
            }
        }
        return fila;
    }

    private static List<Columna> obligatorias() {
        return Arrays.stream(Columna.values()).filter(c -> c.obligatoria).toList();
    }

    // --- Celdas ---

    /**
     * El texto de una celda de cabecera, resolviendo las celdas combinadas: en
     * una región combinada el valor solo está en la celda ancla, y POI
     * devuelve vacío para las demás.
     */
    private static String textoDeCabecera(Sheet hoja, Row fila, int columna) {
        String propio = texto(fila, columna);
        if (!propio.isBlank()) {
            return propio;
        }
        for (CellRangeAddress region : hoja.getMergedRegions()) {
            if (region.isInRange(fila.getRowNum(), columna)) {
                Row anclaje = hoja.getRow(region.getFirstRow());
                return anclaje == null ? "" : texto(anclaje, region.getFirstColumn());
            }
        }
        return "";
    }

    private static String texto(Row fila, Integer columna) {
        if (fila == null || columna == null) {
            return "";
        }
        return texto(fila.getCell(columna));
    }

    private static String texto(Cell celda) {
        if (celda == null) {
            return "";
        }
        return switch (celda.getCellType()) {
            // Los excels que salen de un conversor traen celdas «inlineStr»
            // sin contenido, y para esas POI devuelve null, no cadena vacía.
            case STRING -> celda.getStringCellValue() == null
                    ? "" : celda.getStringCellValue().trim();
            case NUMERIC -> BigDecimal.valueOf(celda.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(celda.getBooleanCellValue());
            case FORMULA -> textoDeFormula(celda);
            default -> "";
        };
    }

    private static String textoDeFormula(Cell celda) {
        try {
            String valor = celda.getStringCellValue();
            return valor == null ? "" : valor.trim();
        } catch (IllegalStateException e) {
            return BigDecimal.valueOf(celda.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
        }
    }

    private static String mayusculas(String texto) {
        return texto.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Forma canónica de un nombre de hoja o de columna: sin acentos, en
     * mayúsculas, sin puntos ni dos puntos ni el símbolo de grado —que es como
     * se escribe la "o" voladita de "Nº"—, sin espacios alrededor de las
     * barras y con los espacios múltiples colapsados.
     */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinAcentos.toUpperCase(Locale.ROOT)
                .replaceAll("[.:°º]", "")
                .replaceAll("\\s*/\\s*", "/")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // --- Errores que el usuario puede arreglar ---

    /** Base de los dos motivos por los que un fichero de taller no se puede leer. */
    public abstract static class TallerExcelException extends Exception {

        protected TallerExcelException(String mensaje) {
            super(mensaje);
        }
    }

    /** El libro no trae ninguna hoja que se llame como la del packing list. */
    public static class HojaNoEncontradaException extends TallerExcelException {

        private final List<String> hojasEncontradas;

        public HojaNoEncontradaException(String buscada, List<String> hojasEncontradas) {
            super("En el fichero del taller no hay ninguna hoja llamada '" + buscada
                    + "'. El libro tiene estas: " + String.join(", ", hojasEncontradas));
            this.hojasEncontradas = List.copyOf(hojasEncontradas);
        }

        /** Para ofrecer al usuario elegir la hoja a mano. */
        public List<String> hojasEncontradas() {
            return hojasEncontradas;
        }
    }

    /** La hoja está, pero le falta alguna columna sin la que no hay packing. */
    public static class ColumnasAusentesException extends TallerExcelException {

        private final List<String> columnasAusentes;
        private final List<String> cabecerasLeidas;

        public ColumnasAusentesException(List<String> columnasAusentes,
                                         List<String> cabecerasLeidas, int fila) {
            super("En la hoja del taller faltan estas columnas: "
                    + String.join(", ", columnasAusentes)
                    + (cabecerasLeidas.isEmpty()
                            ? ". No se ha encontrado ninguna fila de cabecera."
                            : ". En la fila " + fila + " se han leído estas: "
                                    + String.join(", ", cabecerasLeidas)));
            this.columnasAusentes = List.copyOf(columnasAusentes);
            this.cabecerasLeidas = List.copyOf(cabecerasLeidas);
        }

        public List<String> columnasAusentes() {
            return columnasAusentes;
        }

        public List<String> cabecerasLeidas() {
            return cabecerasLeidas;
        }
    }
}

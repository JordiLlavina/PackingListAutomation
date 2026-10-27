package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.puntotres.packinglist.persistence.MemoriaPieles;

/**
 * Lee el excel de producción de Puntotres ("prod APC excel.xlsx", "prod AMI
 * SS27 excel.xlsx") y saca de él lo que hay que cortar.
 *
 * Se lee POR TÍTULO: las columnas cambian de un excel a otro (el de AMI va
 * una columna más a la izquierda que el de APC) y el usuario puso títulos
 * para que se pudiera. Se comparan sin mayúsculas, acentos, puntos ni
 * espacios de más:
 *
 * <ul>
 *   <li>"Ref. APC", "Ref. AMI"...: la que empieza por REF y no es la de
 *       Puntotres. Lleva la referencia (una palabra con alguna cifra:
 *       "PXCBC-F67008", "ULL772.AL245") y, en AMI, el nombre del bolso.</li>
 *   <li>"Ref. Puntotres" (opcional): debajo de la Ref. Puntotres, el nombre
 *       del bolso en APC ("LE NEIGE").</li>
 *   <li>"PIEL" y "COLOR" ("LZZ NEGRO"; con barra, el de cada piel de un
 *       bicolor).</li>
 *   <li>"Cantidad taller": la que se corta, por decisión del usuario.
 *       "Cantidad pedido" (opcional) solo sirve para avisar de una línea con
 *       pedido y sin taller.</li>
 *   <li>Las de PRENSA: cualquier título con PRESS, PRENSA o PREMSA ("PRESS
 *       25/11 max"). Sus bolsos van en su propia orden de corte.</li>
 * </ul>
 *
 * Un bloque va de una FILA DE SUMAS (=SUM en la cantidad del taller) a la
 * siguiente, y su referencia es la primera que haya en él: APC la pone en la
 * fila de sumas y AMI debajo de las líneas de color. La misma referencia en
 * dos bloques (dos "drops" de AMI) suma sus bolsos. Una línea de color es una
 * fila con color y con cantidad de taller o de prensa; las demás (LOGO ORO,
 * "(STUDS NICKEL)", los cinturones del final de APC, que no llevan color ni
 * taller) se saltan sin aviso.
 */
public final class ProduccionPuntotresExcel {

    private static final String TITULO_REFERENCIA = "Ref. <cliente>";
    private static final String TITULO_PIEL = "PIEL";
    private static final String TITULO_COLOR = "COLOR";
    private static final String TITULO_TALLER = "Cantidad taller";

    /** Una sola palabra con alguna cifra: "DROP 1A 30/10/2026" o "BAG" no son una referencia. */
    private static final Pattern REFERENCIA = Pattern.compile("\\S*\\d\\S*");
    private static final Pattern PRENSA = Pattern.compile("PRESS|PRENSA|PREMSA");

    private static final DataFormatter FORMATO = new DataFormatter();

    private ProduccionPuntotresExcel() {
    }

    /**
     * Lanza IllegalArgumentException con un mensaje en español si el fichero
     * no es de producción.
     *
     * Se abre desde un fichero temporal y no desde memoria a propósito: desde
     * memoria POI descomprime todo el libro, imágenes incluidas, y el real de
     * APC lleva un EMF de 104 KB comprimido en 731 bytes que su protección
     * contra «zip bombs» rechaza, así que el excel no se abría. Desde un
     * fichero solo se descomprime lo que se lee, y las fotos no se leen.
     * Bajar ese límite no es la salida: es global y protege todas las subidas.
     */
    public static ProduccionCorte leer(byte[] excel, ClienteCorte cliente) throws IOException {
        Path temporal = Files.createTempFile("excel-produccion-", ".xlsx");
        try {
            Files.write(temporal, excel);
            try (Workbook libro = WorkbookFactory.create(temporal.toFile(), null, true)) {
                return leer(libro.getSheetAt(0), cliente);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    /** Dónde está cada dato; -1 si la columna opcional no está. */
    private record Columnas(int filaTitulos, int referencia, int puntotres, int piel, int color,
                            int taller, int pedido, List<Integer> prensa) {
    }

    private record Linea(int fila, String piel, String color, int cantidad, boolean prensa) {
    }

    /** Lo que va de una fila de sumas a la siguiente. */
    private static final class Bloque {
        final int fila;
        String referencia;
        final List<String> nombreEnReferencia = new ArrayList<>();
        String nombreEnPuntotres = "";
        final List<Linea> lineas = new ArrayList<>();

        Bloque(int fila) {
            this.fila = fila;
        }

        String nombre() {
            String enReferencia = String.join(" ", nombreEnReferencia).replaceAll("\\s+", " ").trim();
            return enReferencia.isEmpty() ? nombreEnPuntotres : enReferencia;
        }
    }

    private static ProduccionCorte leer(Sheet hoja, ClienteCorte cliente) {
        Columnas columnas = columnas(hoja);
        List<String> avisos = new ArrayList<>();
        List<Bloque> bloques = new ArrayList<>();
        Bloque actual = null;
        for (int numero = columnas.filaTitulos() + 1; numero <= hoja.getLastRowNum(); numero++) {
            Row fila = hoja.getRow(numero);
            if (fila == null) {
                continue;
            }
            String enReferencia = texto(fila, columnas.referencia());
            boolean deSumas = esSuma(fila, columnas.taller());
            // Una referencia en un bloque que ya tiene la suya abre otro, aunque
            // le falte la fila de sumas: si no, sus líneas irían a la de arriba.
            boolean otraReferencia = actual != null && actual.referencia != null && esReferencia(enReferencia);
            if (deSumas || actual == null && esReferencia(enReferencia) || otraReferencia) {
                actual = new Bloque(numero);
                bloques.add(actual);
            }
            if (actual == null) {
                continue;
            }
            if (!enReferencia.isEmpty()) {
                if (esReferencia(enReferencia)) {
                    if (actual.referencia == null) {
                        actual.referencia = ReferenciaCorte.normalizar(enReferencia);
                    }
                } else if (actual.referencia == null) {
                    actual.nombreEnReferencia.add(enReferencia);
                }
            }
            if (deSumas) {
                continue;
            }
            String enPuntotres = texto(fila, columnas.puntotres());
            if (actual.nombreEnPuntotres.isEmpty() && !enPuntotres.isEmpty()) {
                actual.nombreEnPuntotres = enPuntotres;
            }

            String color = texto(fila, columnas.color());
            Integer taller = numero(fila, columnas.taller());
            int prensa = columnas.prensa().stream()
                    .map(columna -> numero(fila, columna))
                    .filter(valor -> valor != null && valor > 0)
                    .mapToInt(Integer::intValue).sum();
            if (taller == null && prensa == 0) {
                Integer pedido = numero(fila, columnas.pedido());
                if (pedido != null && !color.isEmpty()) {
                    avisos.add("Fila " + (numero + 1) + " (" + color + "): " + pedido + " bolsos pedidos y "
                            + "ninguno en «Cantidad taller»; no se corta nada");
                }
                continue;
            }
            if (color.isEmpty()) {
                avisos.add("Fila " + (numero + 1) + ": bolsos sin color; no se han cargado");
                continue;
            }
            String piel = texto(fila, columnas.piel());
            if (taller != null) {
                actual.lineas.add(new Linea(numero, piel, color, taller, false));
            }
            if (prensa > 0) {
                actual.lineas.add(new Linea(numero, piel, color, prensa, true));
            }
        }
        return juntar(bloques, cliente, avisos);
    }

    /** Las líneas de cada bloque con su referencia, y lo que el excel dice de sus pieles. */
    private static ProduccionCorte juntar(List<Bloque> bloques, ClienteCorte cliente, List<String> avisos) {
        List<LineaCorte> deVenta = new ArrayList<>();
        List<LineaCorte> dePrensa = new ArrayList<>();
        Map<String, List<String>> pieles = new LinkedHashMap<>();
        Map<String, Map<String, List<String>>> coloresDe = new LinkedHashMap<>();
        for (Bloque bloque : bloques) {
            if (bloque.referencia == null) {
                if (!bloque.lineas.isEmpty()) {
                    avisos.add("Filas " + (bloque.lineas.get(0).fila() + 1) + " a "
                            + (bloque.lineas.get(bloque.lineas.size() - 1).fila() + 1)
                            + ": líneas de color sin referencia en la columna " + TITULO_REFERENCIA
                            + "; no se han cargado");
                }
                continue;
            }
            String referencia = bloque.referencia;
            if (!cliente.seCorta(referencia)) {
                continue;
            }
            if (bloque.lineas.isEmpty()) {
                avisos.add(referencia + " (fila " + (bloque.fila + 1) + "): no trae ninguna línea con color "
                        + "y cantidad; no sale en la tabla");
                continue;
            }
            String nombre = bloque.nombre();
            for (Linea linea : bloque.lineas) {
                List<ColorCorte> deLasPieles = Arrays.stream(linea.color().split("/"))
                        .map(String::trim).filter(parte -> !parte.isEmpty())
                        .map(cliente::colorDeProduccion).toList();
                List<String> nombresDeColor = deLasPieles.stream()
                        .map(unaPiel -> unaPiel.descripcion().isEmpty() ? unaPiel.codigo() : unaPiel.descripcion())
                        .toList();
                // Un solo color: su código y su descripción. Varios (un bicolor):
                // sin código —el del primero no es el del bolso— y todos los
                // nombres juntos, que es como se identifica.
                ColorCorte delBolso = deLasPieles.size() == 1
                        ? deLasPieles.get(0).conBolsos(linea.cantidad())
                        : new ColorCorte("", String.join(" / ", nombresDeColor), linea.cantidad());
                if (linea.prensa()) {
                    delBolso = delBolso.dePrensa();
                }
                // La prensa detrás de los colores de venta: sus órdenes salen al final de cada referencia.
                (linea.prensa() ? dePrensa : deVenta).add(new LineaCorte(referencia, delBolso.codigo(),
                        delBolso.descripcion(), linea.cantidad(), nombre, linea.prensa()));

                if (!linea.piel().isEmpty()) {
                    List<String> suyas = pieles.computeIfAbsent(referencia, clave -> new ArrayList<>());
                    if (suyas.stream().noneMatch(unaPiel -> unaPiel.equalsIgnoreCase(linea.piel()))) {
                        suyas.add(linea.piel());
                    }
                }
                if (!linea.prensa() && deLasPieles.size() > MemoriaPieles.MAXIMO_COMBINACIONES + 1) {
                    avisos.add(referencia + " en " + linea.color() + ": más pieles de las que caben en la "
                            + "tabla (" + (MemoriaPieles.MAXIMO_COMBINACIONES + 1) + "); las de más no salen");
                }
                coloresDe.computeIfAbsent(referencia, clave -> new LinkedHashMap<>())
                        .putIfAbsent(ProduccionCorte.claveDe(delBolso), nombresDeColor);
            }
        }
        // Varias pieles escritas en una referencia van juntas, por decisión del
        // usuario: no se sabe cuál es la buena y las dos dicen algo ("SERRAJE"
        // en un color y "ALABAMA" en otro: ALABAMA es un serraje).
        Map<String, String> pielDe = new LinkedHashMap<>();
        pieles.forEach((referencia, suyas) -> pielDe.put(referencia, String.join(" ", suyas)));
        List<LineaCorte> lineas = new ArrayList<>(deVenta);
        lineas.addAll(dePrensa);
        return new ProduccionCorte(PedidoCorte.agrupar(lineas, cliente::partir, false), pielDe,
                coloresDe, avisos);
    }

    /** La primera fila que tiene los cuatro títulos que hacen falta. */
    private static Columnas columnas(Sheet hoja) {
        for (Row fila : hoja) {
            int referencia = -1;
            int puntotres = -1;
            int piel = -1;
            int color = -1;
            int taller = -1;
            int pedido = -1;
            List<Integer> prensa = new ArrayList<>();
            for (Cell celda : fila) {
                String titulo = normalizar(texto(fila, celda.getColumnIndex()));
                int columna = celda.getColumnIndex();
                if (titulo.isEmpty()) {
                    continue;
                }
                if (titulo.startsWith("REF") && titulo.contains("PUNTOTRES")) {
                    puntotres = primera(puntotres, columna);
                } else if (titulo.startsWith("REF")) {
                    referencia = primera(referencia, columna);
                } else if (titulo.equals("PIEL")) {
                    piel = primera(piel, columna);
                } else if (titulo.equals("COLOR")) {
                    color = primera(color, columna);
                } else if (titulo.contains("CANTIDAD") && titulo.contains("TALLER")) {
                    taller = primera(taller, columna);
                } else if (titulo.contains("CANTIDAD") && titulo.contains("PEDIDO")) {
                    pedido = primera(pedido, columna);
                } else if (PRENSA.matcher(titulo).find()) {
                    prensa.add(columna);
                }
            }
            if (referencia >= 0 && piel >= 0 && color >= 0 && taller >= 0) {
                return new Columnas(fila.getRowNum(), referencia, puntotres, piel, color, taller, pedido,
                        List.copyOf(prensa));
            }
        }
        throw new IllegalArgumentException("no tiene el formato del excel de producción de Puntotres: no "
                + "hay ninguna fila con los títulos «" + TITULO_REFERENCIA + "», «" + TITULO_PIEL + "», «"
                + TITULO_COLOR + "» y «" + TITULO_TALLER + "»");
    }

    private static int primera(int actual, int columna) {
        return actual >= 0 ? actual : columna;
    }

    /** Mayúsculas, sin acentos, sin puntos y con un solo espacio: "Ref. APC" → "REF APC". */
    static String normalizar(String titulo) {
        String sinAcentos = Normalizer.normalize(titulo, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toUpperCase(Locale.ROOT).replace('.', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean esSuma(Row fila, int columna) {
        Cell celda = fila.getCell(columna);
        return celda != null && celda.getCellType() == CellType.FORMULA
                && celda.getCellFormula().toUpperCase(Locale.ROOT).startsWith("SUM(");
    }

    private static boolean esReferencia(String texto) {
        return !texto.isEmpty() && REFERENCIA.matcher(texto).matches();
    }

    private static String texto(Row fila, int columna) {
        if (columna < 0) {
            return "";
        }
        Cell celda = fila.getCell(columna);
        if (celda == null) {
            return "";
        }
        if (celda.getCellType() == CellType.FORMULA) {
            return celda.getCachedFormulaResultType() == CellType.STRING
                    ? limpio(celda.getStringCellValue()) : "";
        }
        return limpio(FORMATO.formatCellValue(celda));
    }

    /** La cantidad de una celda, o null si no es un número ("???", vacía). */
    private static Integer numero(Row fila, int columna) {
        if (columna < 0) {
            return null;
        }
        Cell celda = fila.getCell(columna);
        if (celda == null) {
            return null;
        }
        CellType tipo = celda.getCellType() == CellType.FORMULA
                ? celda.getCachedFormulaResultType() : celda.getCellType();
        if (tipo == CellType.NUMERIC) {
            return (int) Math.round(celda.getNumericCellValue());
        }
        if (tipo == CellType.STRING) {
            try {
                return Integer.parseInt(limpio(celda.getStringCellValue()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }
}

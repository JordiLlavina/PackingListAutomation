package com.puntotres.packinglist.service.corte;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.openxmlformats.schemas.drawingml.x2006.main.CTRelativeRect;

/**
 * Lee el excel de pedido de SMS de APC ("COMMANDE SMS_FALL27_PUNTOTRES.xlsx"):
 * las muestras de venta de la temporada, una fila por referencia y color.
 *
 * Se leen TODAS las hojas que tengan las columnas (el de FALL 27 trae FEMME y
 * HOMME, cada una con sus columnas en otro sitio), y POR TÍTULO, comparado sin
 * mayúsculas, acentos, puntos, espacios ni saltos de línea ("COLORIS
 * \nA.P.C." = "COLORIS A.P.C."). Lo que sale de cada columna:
 *
 * <ul>
 *   <li>CODIGO: la referencia entera, piel + modelo ("PXCBC-F67008"). El
 *       cliente la escribe a veces con un tabulador delante o con espacios
 *       alrededor del guion ("PXCBC -H63044"), y una referencia no lleva
 *       espacios: se quitan todos.</li>
 *   <li>CODE MODELE (opcional): el modelo, que también va dentro de la
 *       referencia; si no cuadran, manda la referencia y se avisa.</li>
 *   <li>MODELE (opcional): el nombre del bolso ("LE NEIGE").</li>
 *   <li>COLORIS A.P.C.: el color del bolso, código y descripción ("LZZ
 *       NOIR"), partido con la regla de APC del excel de producción.</li>
 *   <li>ARTICLE: el nombre de la piel en el proveedor ("ALPACA TOUCH"), y
 *       COLORIS FOURNISSEURS (opcional) su color ("NEGRO", "9032 FONDANTE").
 *       Un bolso de dos pieles las junta con «+» y sus colores con «/»
 *       ("BOMBARDIER … + NOBILE", "ECRU/CAE MARRON FONCÉ"): la primera es la
 *       principal y las demás, combinaciones, cada una con su color si hay
 *       tantos colores como pieles.</li>
 *   <li>COLORIS DOUBLURE en FEMME, FORRO en HOMME (opcionales): el forro, tal
 *       cual ("STILLA 999").</li>
 *   <li>Commande SMS: las muestras. Un número, o un número con texto detrás
 *       ("4 SMS"): manda el número del principio. «PAS DE SMS» o un cero no
 *       se cortan, sin aviso; cualquier otra cosa no se corta y se avisa.</li>
 *   <li>CROQUIS (opcional): las imágenes ancladas en esa celda. La misma imagen
 *       pegada dos veces en una celda cuenta una.</li>
 * </ul>
 */
public final class ApcSmsExcel {

    static final String TITULO_REFERENCIA = "CODIGO";
    static final String TITULO_COLOR = "COLORIS A.P.C.";
    static final String TITULO_PIEL = "ARTICLE";
    static final String TITULO_MUESTRAS = "Commande SMS";

    private static final String CODE_MODELE = clave("CODE MODELE");
    private static final String MODELE = clave("MODELE");
    private static final String COLOR_PIEL = clave("COLORIS FOURNISSEURS");
    private static final List<String> FORRO = List.of(clave("COLORIS DOUBLURE"), clave("FORRO"));
    private static final String CROQUIS = clave("CROQUIS");
    private static final String SIN_SMS = clave("PAS DE SMS");

    /** Hasta dónde se busca la fila de títulos. */
    private static final int FILAS_BUSCANDO_TITULOS = 20;
    private static final Pattern NUMERO_DELANTE = Pattern.compile("^(\\d+)");
    private static final DataFormatter FORMATO = new DataFormatter();

    /** Lo leído y lo que hay que decirle al usuario. */
    public record Lectura(List<LineaSms> lineas, List<String> avisos) {

        public Lectura {
            lineas = List.copyOf(lineas);
            avisos = List.copyOf(avisos);
        }
    }

    private record Columnas(int filaTitulos, int referencia, int color, int piel, int muestras,
                            int modelo, int nombreModelo, int colorPiel, int forro, int croquis) {
    }

    private ApcSmsExcel() {
    }

    /**
     * Lanza IllegalArgumentException con un mensaje en español si ninguna hoja
     * tiene las columnas del excel de SMS. Se abre desde un fichero temporal,
     * como el de producción: desde memoria POI descomprime el libro entero y
     * su protección contra «zip bombs» rechaza algunos dibujos incrustados.
     */
    public static Lectura leer(byte[] excel, ClienteCorte cliente) throws IOException {
        Path temporal = Files.createTempFile("excel-sms-", ".xlsx");
        try {
            Files.write(temporal, excel);
            try (Workbook libro = WorkbookFactory.create(temporal.toFile(), null, true)) {
                return leer(libro, cliente);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static Lectura leer(Workbook libro, ClienteCorte cliente) {
        List<LineaSms> lineas = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        // Una imagen repetida (la misma en varias filas) es un solo objeto:
        // así el Excel de órdenes la guarda una vez.
        Map<String, Optional<Imagen>> imagenes = new HashMap<>();
        boolean algunaHoja = false;
        for (Sheet hoja : libro) {
            Optional<Columnas> columnas = columnas(hoja);
            if (columnas.isEmpty()) {
                if (tieneDatos(hoja)) {
                    avisos.add("La hoja " + hoja.getSheetName() + " no tiene las columnas del excel de "
                            + "SMS (" + titulosObligatorios() + "): no se lee");
                }
                continue;
            }
            algunaHoja = true;
            leerHoja(hoja, columnas.get(), cliente, imagenes, lineas, avisos);
        }
        if (!algunaHoja) {
            throw new IllegalArgumentException("no tiene ninguna hoja con las columnas "
                    + titulosObligatorios() + ": revisa que sea el excel de pedido de SMS");
        }
        avisarNombresDistintos(lineas, avisos);
        return new Lectura(lineas, avisos);
    }

    private static void leerHoja(Sheet hoja, Columnas columnas, ClienteCorte cliente,
                                 Map<String, Optional<Imagen>> imagenes, List<LineaSms> lineas,
                                 List<String> avisos) {
        Map<Integer, List<Croquis>> croquisPorFila = croquisPorFila(hoja, columnas.croquis());
        for (int numero = columnas.filaTitulos() + 1; numero <= hoja.getLastRowNum(); numero++) {
            Row fila = hoja.getRow(numero);
            if (fila == null) {
                continue;
            }
            String donde = hoja.getSheetName() + ", fila " + (numero + 1);
            String referencia = texto(fila, columnas.referencia()).replaceAll("\\s+", "")
                    .toUpperCase(Locale.ROOT);
            String encargo = texto(fila, columnas.muestras());
            if (referencia.isEmpty()) {
                if (muestras(encargo) > 0) {
                    avisos.add(donde + ": tiene «" + TITULO_MUESTRAS + "» pero no «" + TITULO_REFERENCIA
                            + "»: no se corta");
                }
                continue;
            }
            int muestras = muestras(encargo);
            if (muestras < 0) {
                avisos.add(donde + " (" + referencia + "): «" + TITULO_MUESTRAS + "» "
                        + (encargo.isEmpty() ? "está vacía" : "dice «" + encargo + "», que no es un número")
                        + ": no se corta");
                continue;
            }
            if (muestras == 0) {
                continue;
            }
            ReferenciaCorte partida = cliente.partir(referencia);
            String modelo = texto(fila, columnas.modelo()).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            if (partida.tienePiel()) {
                if (!modelo.isEmpty() && !modelo.equals(partida.modelo())) {
                    avisos.add(donde + ": «CODE MODELE» dice " + modelo + " y la referencia " + referencia
                            + " es del modelo " + partida.modelo() + ": manda la referencia");
                }
                modelo = partida.modelo();
            } else if (modelo.isEmpty()) {
                modelo = referencia;
            }
            String textoColor = texto(fila, columnas.color());
            if (textoColor.isEmpty()) {
                avisos.add(donde + " (" + referencia + "): sin «" + TITULO_COLOR + "», sale sin color");
            }
            ColorCorte color = cliente.colorDeProduccion(textoColor.toUpperCase(Locale.ROOT)).conBolsos(muestras);
            List<Imagen> croquis = new ArrayList<>();
            for (Croquis dibujo : croquisPorFila.getOrDefault(numero, List.of())) {
                imagen(dibujo, imagenes, donde, avisos).filter(leida -> !croquis.contains(leida))
                        .ifPresent(croquis::add);
            }
            lineas.add(new LineaSms(hoja.getSheetName(), numero + 1, referencia, modelo,
                    texto(fila, columnas.nombreModelo()), color,
                    pieles(texto(fila, columnas.piel()), texto(fila, columnas.colorPiel()),
                            texto(fila, columnas.forro())),
                    muestras, croquis));
        }
    }

    /**
     * Las muestras de «Commande SMS»: el número del principio ("4 SMS" = 4),
     * 0 si no hay que cortar ninguna («PAS DE SMS», "0") y -1 si no se sabe
     * (vacía o un texto que no empieza por un número).
     */
    static int muestras(String texto) {
        if (clave(texto).equals(SIN_SMS)) {
            return 0;
        }
        Matcher numero = NUMERO_DELANTE.matcher(texto.trim());
        if (!numero.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(numero.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Las pieles de la fila. Dos pieles se escriben con «+» entre ellas y sus
     * colores con «/»; si no hay tantos colores como pieles no se sabe cuál
     * es de cuál, y el color entero va a la principal.
     */
    static PielesArticulo pieles(String articulo, String colorPiel, String forro) {
        List<String> nombres = partir(articulo, "\\+");
        List<String> colores = partir(colorPiel, "/");
        boolean emparejados = nombres.size() > 1 && colores.size() == nombres.size();
        PielCorte principal = new PielCorte(nombres.isEmpty() ? "" : nombres.get(0),
                emparejados ? colores.get(0) : colorPiel);
        List<PielCorte> combinaciones = new ArrayList<>();
        for (int i = 1; i < nombres.size(); i++) {
            combinaciones.add(new PielCorte(nombres.get(i), emparejados ? colores.get(i) : ""));
        }
        return new PielesArticulo(principal, new PielCorte(forro, ""), combinaciones);
    }

    private static List<String> partir(String texto, String separador) {
        List<String> partes = new ArrayList<>();
        for (String parte : texto.split(separador)) {
            if (!parte.isBlank()) {
                partes.add(parte.trim());
            }
        }
        return partes;
    }

    /** El mismo modelo con dos nombres es casi siempre un CODE MODELE mal copiado de la fila de al lado. */
    private static void avisarNombresDistintos(List<LineaSms> lineas, List<String> avisos) {
        Map<String, LineaSms> primera = new LinkedHashMap<>();
        Map<String, List<String>> avisados = new HashMap<>();
        for (LineaSms linea : lineas) {
            if (linea.nombreModelo().isEmpty()) {
                continue;
            }
            LineaSms anterior = primera.putIfAbsent(linea.modelo(), linea);
            if (anterior == null || clave(anterior.nombreModelo()).equals(clave(linea.nombreModelo()))) {
                continue;
            }
            List<String> yaDichos = avisados.computeIfAbsent(linea.modelo(), modelo -> new ArrayList<>());
            if (!yaDichos.contains(clave(linea.nombreModelo()))) {
                yaDichos.add(clave(linea.nombreModelo()));
                avisos.add("El modelo " + linea.modelo() + " se llama «" + anterior.nombreModelo() + "» en "
                        + anterior.donde() + " (" + anterior.referencia() + ") y «" + linea.nombreModelo()
                        + "» en " + linea.donde() + " (" + linea.referencia() + "): revisa el «CODE MODELE» y "
                        + "el «CODIGO» de esas filas");
            }
        }
    }

    private static Optional<Columnas> columnas(Sheet hoja) {
        int ultima = Math.min(hoja.getLastRowNum(), FILAS_BUSCANDO_TITULOS);
        for (int numero = hoja.getFirstRowNum(); numero <= ultima; numero++) {
            Row fila = hoja.getRow(numero);
            if (fila == null) {
                continue;
            }
            Map<String, Integer> titulos = new HashMap<>();
            for (Cell celda : fila) {
                titulos.putIfAbsent(clave(texto(fila, celda.getColumnIndex())), celda.getColumnIndex());
            }
            Integer referencia = titulos.get(clave(TITULO_REFERENCIA));
            Integer color = titulos.get(clave(TITULO_COLOR));
            Integer piel = titulos.get(clave(TITULO_PIEL));
            Integer muestras = titulos.get(clave(TITULO_MUESTRAS));
            if (referencia == null || color == null || piel == null || muestras == null) {
                continue;
            }
            int forro = FORRO.stream().filter(titulos::containsKey).map(titulos::get).findFirst().orElse(-1);
            return Optional.of(new Columnas(numero, referencia, color, piel, muestras,
                    titulos.getOrDefault(CODE_MODELE, -1), titulos.getOrDefault(MODELE, -1),
                    titulos.getOrDefault(COLOR_PIEL, -1), forro, titulos.getOrDefault(CROQUIS, -1)));
        }
        return Optional.empty();
    }

    private static String titulosObligatorios() {
        return "«" + TITULO_REFERENCIA + "», «" + TITULO_COLOR + "», «" + TITULO_PIEL + "» y «"
                + TITULO_MUESTRAS + "»";
    }

    private static boolean tieneDatos(Sheet hoja) {
        for (Row fila : hoja) {
            for (Cell celda : fila) {
                if (!texto(fila, celda.getColumnIndex()).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Fila → las imágenes cuya esquina de arriba cae en la columna CROQUIS de
     * esa fila. Los otros dibujos de la hoja (las elipses con que el cliente
     * marca celdas) no son imágenes y no cuentan.
     */
    private static Map<Integer, List<Croquis>> croquisPorFila(Sheet hoja, int columna) {
        Map<Integer, List<Croquis>> porFila = new HashMap<>();
        if (columna < 0 || !(hoja instanceof XSSFSheet xssf) || xssf.getDrawingPatriarch() == null) {
            return porFila;
        }
        for (XSSFShape forma : xssf.getDrawingPatriarch().getShapes()) {
            if (!(forma instanceof XSSFPicture imagen)
                    || !(imagen.getAnchor() instanceof XSSFClientAnchor ancla)) {
                continue;
            }
            int desde = Math.min(ancla.getCol1(), ancla.getCol2());
            int hasta = Math.max(ancla.getCol1(), ancla.getCol2());
            if (desde <= columna && columna <= hasta) {
                porFila.computeIfAbsent(ancla.getRow1(), fila -> new ArrayList<>())
                        .add(new Croquis(imagen.getPictureData().getData(), recorte(imagen)));
            }
        }
        return porFila;
    }

    /**
     * Una imagen pegada en la celda CROQUIS y cuánto de ella enseña Excel:
     * el cliente recorta las imágenes en Excel (el «srcRect» del dibujo), y
     * lo que queda fuera del recorte sigue en el fichero. Cada lado es la
     * fracción que se quita, de 0 a 1.
     */
    record Croquis(byte[] bytes, double[] recorte) {
    }

    /** El recorte de Excel de una imagen (izquierda, arriba, derecha, abajo); sin recorte, ceros. */
    private static double[] recorte(XSSFPicture imagen) {
        double[] lados = new double[4];
        var relleno = imagen.getCTPicture().getBlipFill();
        if (relleno == null || !relleno.isSetSrcRect()) {
            return lados;
        }
        CTRelativeRect rectangulo = relleno.getSrcRect();
        lados[0] = rectangulo.isSetL() ? fraccion(rectangulo.getL()) : 0;
        lados[1] = rectangulo.isSetT() ? fraccion(rectangulo.getT()) : 0;
        lados[2] = rectangulo.isSetR() ? fraccion(rectangulo.getR()) : 0;
        lados[3] = rectangulo.isSetB() ? fraccion(rectangulo.getB()) : 0;
        return lados;
    }

    /**
     * Un ST_Percentage de DrawingML: un entero en milésimas de punto
     * porcentual (10423 = 10,423 %) o un texto con «%». Negativo amplía la
     * imagen en vez de recortarla, que aquí no hace falta: cuenta como cero.
     */
    private static double fraccion(Object valor) {
        double fraccion;
        if (valor instanceof Number numero) {
            fraccion = numero.doubleValue() / 100_000;
        } else {
            String texto = String.valueOf(valor).trim();
            try {
                fraccion = texto.endsWith("%")
                        ? Double.parseDouble(texto.substring(0, texto.length() - 1)) / 100
                        : Double.parseDouble(texto) / 100_000;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return Math.max(0, Math.min(1, fraccion));
    }

    /** Lo que Excel enseña de la imagen; entera si el recorte no deja nada. */
    static BufferedImage recortar(BufferedImage original, double[] recorte) {
        int ancho = original.getWidth();
        int alto = original.getHeight();
        int x0 = (int) Math.round(ancho * recorte[0]);
        int y0 = (int) Math.round(alto * recorte[1]);
        int x1 = ancho - (int) Math.round(ancho * recorte[2]);
        int y1 = alto - (int) Math.round(alto * recorte[3]);
        if (x1 - x0 < 1 || y1 - y0 < 1 || (x0 == 0 && y0 == 0 && x1 == ancho && y1 == alto)) {
            return original;
        }
        return original.getSubimage(x0, y0, x1 - x0, y1 - y0);
    }

    /**
     * La imagen lista para la orden de corte: recortada como en Excel,
     * reducida, sobre blanco (un PNG transparente saldría negro en JPEG) y en
     * JPEG. Una que Java no sabe leer (un EMF) se queda fuera con aviso.
     */
    private static Optional<Imagen> imagen(Croquis croquis, Map<String, Optional<Imagen>> leidas, String donde,
                                           List<String> avisos) {
        return leidas.computeIfAbsent(huella(croquis.bytes()) + Arrays.toString(croquis.recorte()), huella -> {
            try {
                BufferedImage original = ImageIO.read(new ByteArrayInputStream(croquis.bytes()));
                if (original == null) {
                    avisos.add(donde + ": el croquis no es una imagen que se pueda leer (¿un dibujo EMF?): "
                            + "sale sin él");
                    return Optional.empty();
                }
                BufferedImage reducida = ReductorImagen.reducir(recortar(original, croquis.recorte()),
                        ReductorImagen.LADO_MAXIMO);
                return Optional.of(new Imagen(ReductorImagen.jpeg(reducida), reducida.getWidth(),
                        reducida.getHeight()));
            } catch (IOException e) {
                avisos.add(donde + ": el croquis no se ha podido leer (" + e.getMessage() + "): sale sin él");
                return Optional.empty();
            }
        });
    }

    private static String huella(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** El texto de la celda, sin tabuladores ni espacios de más; "" si no hay columna o celda. */
    private static String texto(Row fila, int columna) {
        if (columna < 0) {
            return "";
        }
        Cell celda = fila.getCell(columna);
        if (celda == null) {
            return "";
        }
        String valor;
        if (celda.getCellType() == CellType.FORMULA) {
            valor = switch (celda.getCachedFormulaResultType()) {
                case STRING -> celda.getStringCellValue();
                case NUMERIC -> FORMATO.formatRawCellContents(celda.getNumericCellValue(),
                        celda.getCellStyle().getDataFormat(), celda.getCellStyle().getDataFormatString());
                default -> "";
            };
        } else {
            valor = FORMATO.formatCellValue(celda);
        }
        return valor == null ? "" : valor.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Mayúsculas, sin acentos y solo letras y cifras: "COLORIS \nA.P.C." → "COLORISAPC". */
    static String clave(String texto) {
        String sinAcentos = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}

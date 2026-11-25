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
import org.apache.poi.ss.usermodel.ShapeTypes;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.model.ThemesTable;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFSimpleShape;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPercentage;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveFixedPercentage;
import org.openxmlformats.schemas.drawingml.x2006.main.CTRelativeRect;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSRgbColor;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSchemeColor;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSolidColorFillProperties;

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
 *       NOIR"), partido con la regla de APC del excel de producción. En la
 *       misma celda el cliente dibuja una redondita (una elipse) del color
 *       de la muestra: se lee su relleno.</li>
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
        Map<Integer, String> redonditas = redonditasPorFila(hoja, columnas.color());
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
                    texto(fila, columnas.nombreModelo()), color, redonditas.getOrDefault(numero, ""),
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
     * Fila → el color de la redondita que el cliente dibuja en la celda del
     * color (una elipse rellena), en RGB hexadecimal ("273C18"). Las de
     * tamaño cero no se ven y no cuentan: el real tiene varias, restos de
     * copiar celdas. Con dos en la misma celda manda la de encima, que es la
     * última del dibujo.
     */
    private static Map<Integer, String> redonditasPorFila(Sheet hoja, int columna) {
        Map<Integer, String> porFila = new HashMap<>();
        if (!(hoja instanceof XSSFSheet xssf) || xssf.getDrawingPatriarch() == null) {
            return porFila;
        }
        Map<String, String> tema = colores(xssf.getWorkbook().getTheme());
        for (XSSFShape forma : xssf.getDrawingPatriarch().getShapes()) {
            if (!(forma instanceof XSSFSimpleShape figura)
                    || !(figura.getAnchor() instanceof XSSFClientAnchor ancla)
                    || Math.min(ancla.getCol1(), ancla.getCol2()) > columna
                    || Math.max(ancla.getCol1(), ancla.getCol2()) < columna || sinTamano(ancla)) {
                continue;
            }
            CTShapeProperties propiedades = figura.getCTShape().getSpPr();
            if (propiedades == null || !propiedades.isSetPrstGeom() || !propiedades.isSetSolidFill()
                    || propiedades.getPrstGeom().getPrst().intValue() != ShapeTypes.ELLIPSE) {
                continue;
            }
            colorDe(propiedades.getSolidFill(), tema).ifPresent(rgb -> porFila.put(ancla.getRow1(), rgb));
        }
        return porFila;
    }

    private static boolean sinTamano(XSSFClientAnchor ancla) {
        return (ancla.getRow1() == ancla.getRow2() && ancla.getDy1() == ancla.getDy2())
                || (ancla.getCol1() == ancla.getCol2() && ancla.getDx1() == ancla.getDx2());
    }

    /**
     * Los colores del tema de Office, para un libro que no trae el suyo, en
     * el orden en que los numera Excel (el de {@link ThemesTable#getThemeColor}).
     */
    private static final List<String> NOMBRES_TEMA = List.of("lt1", "dk1", "lt2", "dk2", "accent1", "accent2",
            "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink");
    private static final List<String> TEMA_OFFICE = List.of("FFFFFF", "000000", "E7E6E6", "44546A", "4472C4",
            "ED7D31", "A5A5A5", "FFC000", "5B9BD5", "70AD47", "0563C1", "954F72");

    /** Nombre del color del tema ("accent2") → su RGB; los de texto y fondo, con sus dos nombres. */
    private static Map<String, String> colores(ThemesTable tema) {
        Map<String, String> colores = new HashMap<>();
        for (int i = 0; i < NOMBRES_TEMA.size(); i++) {
            XSSFColor suyo = tema == null ? null : tema.getThemeColor(i);
            byte[] rgb = suyo == null ? null : suyo.getRGB();
            colores.put(NOMBRES_TEMA.get(i), rgb != null && rgb.length == 3
                    ? HexFormat.of().withUpperCase().formatHex(rgb) : TEMA_OFFICE.get(i));
        }
        colores.put("tx1", colores.get("dk1"));
        colores.put("bg1", colores.get("lt1"));
        colores.put("tx2", colores.get("dk2"));
        colores.put("bg2", colores.get("lt2"));
        return colores;
    }

    /**
     * El RGB del relleno: directo ("273C18") o un color del tema, y en los
     * dos casos con los retoques de luz que Excel pone al elegir «más oscuro»
     * o «más claro» en la paleta (lumMod, lumOff, shade, tint).
     */
    private static Optional<String> colorDe(CTSolidColorFillProperties relleno, Map<String, String> tema) {
        byte[] rgb;
        double[] luz;
        if (relleno.isSetSrgbClr()) {
            CTSRgbColor color = relleno.getSrgbClr();
            rgb = color.getVal();
            luz = luz(color.getLumModList(), color.getLumOffList(), color.getShadeList(), color.getTintList());
        } else if (relleno.isSetSchemeClr()) {
            CTSchemeColor color = relleno.getSchemeClr();
            String delTema = tema.get(color.getVal().toString());
            if (delTema == null) {
                return Optional.empty();
            }
            rgb = HexFormat.of().parseHex(delTema);
            luz = luz(color.getLumModList(), color.getLumOffList(), color.getShadeList(), color.getTintList());
        } else if (relleno.isSetSysClr() && relleno.getSysClr().getLastClr() != null) {
            rgb = relleno.getSysClr().getLastClr();
            luz = new double[] {1, 0, 1, 1};
        } else {
            return Optional.empty();
        }
        if (rgb == null || rgb.length != 3) {
            return Optional.empty();
        }
        return Optional.of(HexFormat.of().withUpperCase().formatHex(retocar(rgb, luz)));
    }

    /** lumMod, lumOff, shade y tint, en fracciones; los que no están, sin efecto. */
    private static double[] luz(List<CTPercentage> lumMod, List<CTPercentage> lumOff,
                                List<CTPositiveFixedPercentage> shade, List<CTPositiveFixedPercentage> tint) {
        return new double[] {
                lumMod.isEmpty() ? 1 : porcentaje(lumMod.get(0).getVal()),
                lumOff.isEmpty() ? 0 : porcentaje(lumOff.get(0).getVal()),
                shade.isEmpty() ? 1 : porcentaje(shade.get(0).getVal()),
                tint.isEmpty() ? 1 : porcentaje(tint.get(0).getVal())};
    }

    /** Oscurece (shade), aclara (tint) y cambia la luminosidad en HSL (lumMod, lumOff). */
    static byte[] retocar(byte[] rgb, double[] luz) {
        double[] canal = new double[3];
        for (int i = 0; i < 3; i++) {
            double valor = (rgb[i] & 0xFF) / 255.0;
            valor *= luz[2];
            valor += (1 - valor) * (1 - luz[3]);
            canal[i] = valor;
        }
        double max = Math.max(canal[0], Math.max(canal[1], canal[2]));
        double min = Math.min(canal[0], Math.min(canal[1], canal[2]));
        double l = (max + min) / 2;
        double s = 0;
        double h = 0;
        if (max != min) {
            double d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            h = max == canal[0] ? (canal[1] - canal[2]) / d + (canal[1] < canal[2] ? 6 : 0)
                    : max == canal[1] ? (canal[2] - canal[0]) / d + 2
                    : (canal[0] - canal[1]) / d + 4;
            h /= 6;
        }
        l = Math.max(0, Math.min(1, l * luz[0] + luz[1]));
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        double p = 2 * l - q;
        byte[] retocado = new byte[3];
        double[] desplazamiento = {1.0 / 3, 0, -1.0 / 3};
        for (int i = 0; i < 3; i++) {
            double valor = s == 0 ? l : tono(p, q, h + desplazamiento[i]);
            retocado[i] = (byte) Math.round(Math.max(0, Math.min(1, valor)) * 255);
        }
        return retocado;
    }

    private static double tono(double p, double q, double t) {
        t = t < 0 ? t + 1 : t > 1 ? t - 1 : t;
        if (t < 1.0 / 6) {
            return p + (q - p) * 6 * t;
        }
        if (t < 0.5) {
            return q;
        }
        if (t < 2.0 / 3) {
            return p + (q - p) * (2.0 / 3 - t) * 6;
        }
        return p;
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
        return Math.max(0, Math.min(1, porcentaje(valor)));
    }

    /** Un ST_Percentage de DrawingML en fracción, sin recortar (50000 = 0,5); 0 si no se entiende. */
    private static double porcentaje(Object valor) {
        if (valor instanceof Number numero) {
            return numero.doubleValue() / 100_000;
        }
        String texto = String.valueOf(valor).trim();
        try {
            return texto.endsWith("%")
                    ? Double.parseDouble(texto.substring(0, texto.length() - 1)) / 100
                    : Double.parseDouble(texto) / 100_000;
        } catch (NumberFormatException e) {
            return 0;
        }
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

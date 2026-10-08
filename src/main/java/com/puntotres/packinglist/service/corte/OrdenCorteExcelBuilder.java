package com.puntotres.packinglist.service.corte;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Escribe las órdenes de corte de una temporada en un solo Excel, con UNA
 * HOJA POR FICHA, es decir, por modelo, piel y color. La hoja se llama con la
 * referencia ENTERA, que es modelo + piel, y el color ("ULL027.AL0103 - 001
 * BLACK", "PXCBC-F67008 - LZZ NOIR"): el mismo modelo en el mismo color va a
 * menudo en dos pieles (ULL027.AL0103 y ULL027.AL0216, las dos en negro), y
 * con el modelo solo no se distinguirían. Cada ficha es una página A4
 * apaisada al imprimir.
 *
 * La ficha es la del Word que el usuario retocó a mano: arriba, la foto del
 * modelo a la izquierda y a la derecha cliente y temporada en pequeño, y en
 * grande el modelo, el color y los bolsos. Debajo, las pieles —principal, de
 * combinación y forro—, una por renglón: el nombre a la derecha de la primera
 * columna, su COLOR en la segunda y un recuadro vacío en la tercera, donde se
 * pega la muestra de piel una vez impresa.
 *
 * Todo en puntos, que es como mide Excel el alto de las filas. Cada ficha
 * tiene que caber en UNA página, porque se le pega la muestra y se cuelga en
 * el puesto: las alturas son fijas y la de cada piel se calcula con lo que
 * queda debajo de la banda. La impresión va a una escala FIJA, calculada para
 * que la ficha quepa en la página con margen (ver ESCALA_IMPRESION).
 */
public class OrdenCorteExcelBuilder {

    private static final double PUNTOS_POR_CM = 72 / 2.54;

    /**
     * Excel imprime las columnas un 4-5% más anchas de lo que las pinta en
     * pantalla (medido abriendo el ejemplo en Excel: al 100% la tercera
     * columna se iba a otra página, al 95% cabía justa), y cuánto depende de
     * la impresora. Al 90% sobra centímetro y medio.
     */
    static final int ESCALA_IMPRESION = 90;

    /**
     * A4 apaisado con márgenes de 1,2 cm: 27,3 x 18,6 cm útiles en el papel,
     * que en la hoja, a la escala de impresión, son algo más.
     */
    static final double MARGEN_PULGADAS = 1.2 / 2.54;
    static final double ALTO_UTIL = 18.6 * PUNTOS_POR_CM * 100 / ESCALA_IMPRESION;

    /**
     * Las tres columnas: foto y nombre de piel (10 cm en la hoja, como la dejó
     * el usuario en el ejemplo de Word), color de la piel y el recuadro de la
     * muestra. 26,5 cm en la hoja, que al 90% y con lo que Excel ensancha al
     * imprimir se quedan en unos 25 de los 27,3 del papel.
     */
    static final double ANCHO_NOMBRE_CM = 10;
    static final double ANCHO_COLOR_CM = 5;
    static final double ANCHO_RECUADRO_CM = 11.5;

    /** Banda superior de 7 cm: foto a la izquierda, datos a la derecha. */
    static final float[] FILAS_BANDA = {20, 52, 40, 52, 34.4f};
    static final double ALTO_BANDA = suma(FILAS_BANDA);
    /** Aire entre la banda y la primera piel. */
    static final float SEPARACION = 17;
    /** Aire entre dos pieles, para que los recuadros no se toquen. */
    static final float ESPACIO_ENTRE_PIELES = 11;
    /** Una piel sola no necesita media página: 6 cm de recuadro sobran. */
    static final float ALTO_MAXIMO_PIEL = 170;
    /** Un margen para que Excel no se pase de página por un redondeo. */
    static final float RESERVA = 12;

    /**
     * El rótulo de cada piel y de su color van a 14 pt; el nombre, a 28 en
     * negrita y el color a 22, los dos bajando lo justo para caber en su
     * renglón —1,6 cm en el papel con seis pieles—, porque lo que no cabe en una fila de
     * alto fijo no se ve.
     */
    private static final int TAMANO_ROTULO = 14;
    private static final int[] TAMANOS_NOMBRE = {28, 26, 24, 22, 20, 18, 16, 14, 12, 11, 10};
    private static final int[] TAMANOS_COLOR = {22, 20, 18, 16, 14, 12, 11, 10};
    private static final int[] TAMANOS_COLOR_MODELO = {28, 26, 24, 22, 20, 18, 16, 14};
    /** Alto de una línea de Calibri a interlineado sencillo: 1,22 veces el cuerpo. */
    private static final double INTERLINEADO = 1.22;
    /**
     * Ancho de una letra de Calibri en negrita, en cuerpos, un poco por lo
     * alto para que sobre y no falte. Los nombres pueden llegar en mayúsculas.
     */
    private static final double ANCHO_MINUSCULA = 0.52;
    private static final double ANCHO_MAYUSCULA = 0.64;
    private static final double ANCHO_ESPACIO = 0.25;
    /** Al partir en líneas se pierde el final de cada una: la palabra que no cabe baja entera. */
    private static final double PERDIDA_AL_PARTIR = 1.15;
    /** El margen interior que Excel deja a cada lado del texto de una celda. */
    private static final double MARGEN_CELDA = 5;
    /** Lo que se deja libre en cada renglón, para no apurar la última línea. */
    private static final double HOLGURA = 3;
    /** Aire alrededor de la foto, para que no toque el borde de la página. */
    private static final double AIRE_FOTO = 4;

    private static final int COL_NOMBRE = 0;
    private static final int COL_COLOR = 1;
    private static final int COL_RECUADRO = 2;

    /** Excel no admite más de 31 caracteres en el nombre de una hoja. */
    private static final int MAXIMO_NOMBRE_HOJA = 31;
    private static final String PROHIBIDOS_EN_HOJA = "[\\\\/?*\\[\\]:]";

    private static final byte[] GRIS = {0x59, 0x59, 0x59};
    private static final byte[] NEGRO = {0, 0, 0};
    private static final String FUENTE = "Calibri";

    public byte[] generar(List<OrdenCorte> ordenes) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Estilos estilos = new Estilos(libro);
            Map<Imagen, Integer> fotos = new IdentityHashMap<>();
            Set<String> usados = new LinkedHashSet<>();
            for (OrdenCorte orden : ordenes) {
                String nombre = nombreUnico(nombreHoja(orden), usados);
                escribirHoja(libro, libro.createSheet(nombre), orden, estilos, fotos);
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    /**
     * La referencia entera y el color: "ULL027.AL0103 - 001 BLACK", o
     * "PXCBC-F67008 - LZZ" si el color no tiene descripción.
     */
    static String nombreHoja(OrdenCorte orden) {
        String referencia = orden.referencia() == null || orden.referencia().isBlank()
                ? orden.modelo() : orden.referencia();
        return orden.color().isBlank() ? referencia : referencia + " - " + orden.color();
    }

    /**
     * Lo que Excel admite: sin \ / ? * [ ] : (los colores de AMI traen barras,
     * "NOIR/ARGENT VIBRE") y con 31 caracteres como mucho. Si al recortarlo
     * coincide con otro, se le pone " (2)" detrás. Excel no distingue
     * mayúsculas en los nombres de hoja.
     */
    static String nombreUnico(String deseado, Set<String> usados) {
        String nombre = recortar(deseado.replaceAll(PROHIBIDOS_EN_HOJA, "-").replaceAll("\\s+", " ")
                .trim().replaceAll("^'+|'+$", ""), MAXIMO_NOMBRE_HOJA);
        if (nombre.isEmpty()) {
            nombre = "Orden";
        }
        String unico = nombre;
        for (int intento = 2; usados.contains(unico.toLowerCase(Locale.ROOT)); intento++) {
            String sufijo = " (" + intento + ")";
            unico = recortar(nombre, MAXIMO_NOMBRE_HOJA - sufijo.length()) + sufijo;
        }
        usados.add(unico.toLowerCase(Locale.ROOT));
        return unico;
    }

    private static String recortar(String texto, int limite) {
        return texto.length() <= limite ? texto : texto.substring(0, limite).trim();
    }

    private void escribirHoja(XSSFWorkbook libro, XSSFSheet hoja, OrdenCorte orden,
                              Estilos estilos, Map<Imagen, Integer> fotos) {
        hoja.setDisplayGridlines(false);
        hoja.setColumnWidth(COL_NOMBRE, anchoColumna(ANCHO_NOMBRE_CM));
        hoja.setColumnWidth(COL_COLOR, anchoColumna(ANCHO_COLOR_CM));
        hoja.setColumnWidth(COL_RECUADRO, anchoColumna(ANCHO_RECUADRO_CM));
        configurarImpresion(hoja);

        int ultima = escribirFicha(libro, hoja, hoja.createDrawingPatriarch(), 0, orden, estilos, fotos);
        libro.setPrintArea(libro.getSheetIndex(hoja), COL_NOMBRE, COL_RECUADRO, 0, ultima);
    }

    private static void configurarImpresion(XSSFSheet hoja) {
        PrintSetup impresion = hoja.getPrintSetup();
        impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
        impresion.setLandscape(true);
        impresion.setScale((short) ESCALA_IMPRESION);
        hoja.setFitToPage(false);
        hoja.setHorizontallyCenter(true);
        for (PageMargin margen : new PageMargin[] {PageMargin.TOP, PageMargin.BOTTOM,
                PageMargin.LEFT, PageMargin.RIGHT}) {
            hoja.setMargin(margen, MARGEN_PULGADAS);
        }
        hoja.setMargin(PageMargin.HEADER, 0);
        hoja.setMargin(PageMargin.FOOTER, 0);
    }

    /** Escribe una ficha a partir de {@code inicio} y devuelve su última fila. */
    private int escribirFicha(XSSFWorkbook libro, XSSFSheet hoja, XSSFDrawing dibujo, int inicio,
                              OrdenCorte orden, Estilos estilos, Map<Imagen, Integer> fotos) {
        for (int i = 0; i < FILAS_BANDA.length; i++) {
            hoja.createRow(inicio + i).setHeightInPoints(FILAS_BANDA[i]);
        }
        datoDeBanda(hoja, inicio, estilos.texto(orden.cliente().toUpperCase(Locale.ROOT)
                + " | Temporada " + orden.temporada(), 12, false, GRIS), estilos.derecha);
        datoDeBanda(hoja, inicio + 1, estilos.texto(orden.modelo(), 40, true, NEGRO), estilos.derecha);
        double anchoDatos = puntos(ANCHO_COLOR_CM + ANCHO_RECUADRO_CM) - 2 * MARGEN_CELDA;
        datoDeBanda(hoja, inicio + 2, estilos.rotuloYValor("Color  ", 12, orden.color(),
                tamanoEnUnaLinea(orden.color(), anchoDatos - anchoTexto("Color  ", 12),
                        TAMANOS_COLOR_MODELO)), estilos.derecha);
        datoDeBanda(hoja, inicio + 3, estilos.rotuloYValor("Bolsos  ", 12,
                String.valueOf(orden.bolsos()), 40), estilos.derecha);
        // Sin foto el hueco se queda en blanco: un rótulo ahí no le dice nada al cortador.
        if (orden.fotoPrincipal() != null) {
            foto(libro, dibujo, hoja, inicio, orden.fotoPrincipal(), fotos);
        }

        int fila = inicio + FILAS_BANDA.length;
        hoja.createRow(fila++).setHeightInPoints(SEPARACION);
        List<String[]> renglones = renglones(orden.pieles());
        float alto = altoPiel(renglones.size());
        for (int i = 0; i < renglones.size(); i++) {
            String[] renglon = renglones.get(i);
            XSSFRow filaPiel = hoja.createRow(fila);
            filaPiel.setHeightInPoints(alto);
            XSSFCell nombre = filaPiel.createCell(COL_NOMBRE);
            nombre.setCellStyle(estilos.nombrePiel);
            nombre.setCellValue(estilos.rotuloEncimaDeValor(renglon[0].toUpperCase(Locale.ROOT),
                    renglon[1], tamanoNombre(renglon[1], alto)));
            XSSFCell color = filaPiel.createCell(COL_COLOR);
            color.setCellStyle(estilos.colorPiel);
            color.setCellValue(estilos.rotuloEncimaDeValor("COLOR", renglon[2],
                    tamanoColor(renglon[2], alto)));
            filaPiel.createCell(COL_RECUADRO).setCellStyle(estilos.recuadro);
            if (i < renglones.size() - 1) {
                hoja.createRow(++fila).setHeightInPoints(ESPACIO_ENTRE_PIELES);
            }
            fila++;
        }
        return fila - 1;
    }

    /** Rótulo, nombre y color de cada piel, en el orden de la ficha. */
    private static List<String[]> renglones(PielesArticulo pieles) {
        List<String[]> renglones = new ArrayList<>();
        renglones.add(new String[] {"Piel", pieles.principal().nombre(), pieles.principal().color()});
        for (int i = 0; i < pieles.combinaciones().size(); i++) {
            PielCorte combinacion = pieles.combinaciones().get(i);
            renglones.add(new String[] {"Combinación " + (i + 1), combinacion.nombre(), combinacion.color()});
        }
        if (pieles.tieneForro()) {
            renglones.add(new String[] {"Forro", pieles.forro().nombre(), pieles.forro().color()});
        }
        return renglones;
    }

    private static void datoDeBanda(XSSFSheet hoja, int fila, XSSFRichTextString texto,
                                    XSSFCellStyle estilo) {
        XSSFRow suya = hoja.getRow(fila);
        XSSFCell celda = suya.createCell(COL_COLOR);
        celda.setCellStyle(estilo);
        celda.setCellValue(texto);
        suya.createCell(COL_RECUADRO).setCellStyle(estilo);
        hoja.addMergedRegion(new CellRangeAddress(fila, fila, COL_COLOR, COL_RECUADRO));
    }

    /**
     * La foto encajada en el hueco de la izquierda de la banda sin deformarla
     * (manda el lado que antes llega al borde) y centrada en él. Una foto que
     * sale en varias fichas se guarda una sola vez en el libro.
     */
    private static void foto(XSSFWorkbook libro, XSSFDrawing dibujo, XSSFSheet hoja, int inicio,
                             Imagen imagen, Map<Imagen, Integer> fotos) {
        double anchoHueco = puntos(ANCHO_NOMBRE_CM) - 2 * AIRE_FOTO;
        double altoHueco = ALTO_BANDA - 2 * AIRE_FOTO;
        double escala = Math.min(anchoHueco / imagen.ancho(), altoHueco / imagen.alto());
        double ancho = imagen.ancho() * escala;
        double alto = imagen.alto() * escala;
        double x = AIRE_FOTO + (anchoHueco - ancho) / 2;
        double y = AIRE_FOTO + (altoHueco - alto) / 2;
        int indice = fotos.computeIfAbsent(imagen,
                foto -> libro.addPicture(foto.jpeg(), Workbook.PICTURE_TYPE_JPEG));

        int[] desde = filaYResto(hoja, inicio, y);
        int[] hasta = filaYResto(hoja, inicio, y + alto);
        XSSFClientAnchor ancla = new XSSFClientAnchor(
                (int) Math.round(x * Units.EMU_PER_POINT), desde[1],
                (int) Math.round((x + ancho) * Units.EMU_PER_POINT), hasta[1],
                COL_NOMBRE, desde[0], COL_NOMBRE, hasta[0]);
        ancla.setAnchorType(XSSFClientAnchor.AnchorType.MOVE_AND_RESIZE);
        dibujo.createPicture(ancla, indice);
    }

    /** La fila (desde {@code inicio}) donde cae una altura en puntos y lo que sobra, en EMU. */
    private static int[] filaYResto(XSSFSheet hoja, int inicio, double puntos) {
        int fila = inicio;
        double restante = puntos;
        while (restante > hoja.getRow(fila).getHeightInPoints() && hoja.getRow(fila + 1) != null) {
            restante -= hoja.getRow(fila).getHeightInPoints();
            fila++;
        }
        return new int[] {fila, (int) Math.round(Math.max(0, restante) * Units.EMU_PER_POINT)};
    }

    /** Alto de cada renglón de piel: lo que queda debajo de la banda, a partes iguales. */
    static float altoPiel(int pieles) {
        double disponible = ALTO_UTIL - ALTO_BANDA - SEPARACION - RESERVA
                - (pieles - 1) * ESPACIO_ENTRE_PIELES;
        return (float) Math.min(ALTO_MAXIMO_PIEL, Math.floor(disponible / pieles));
    }

    /** El mayor tamaño con el que el nombre cabe en su renglón, debajo de su rótulo. */
    static int tamanoNombre(String nombre, float altoRenglon) {
        return tamanoQueCabe(nombre, puntos(ANCHO_NOMBRE_CM), altoRenglon, TAMANOS_NOMBRE);
    }

    /** Lo mismo para el color de la piel, en su columna. */
    static int tamanoColor(String color, float altoRenglon) {
        return tamanoQueCabe(color, puntos(ANCHO_COLOR_CM), altoRenglon, TAMANOS_COLOR);
    }

    private static int tamanoQueCabe(String texto, double anchoColumna, float altoRenglon, int[] tamanos) {
        for (int tamano : tamanos) {
            if (altoNecesario(texto, tamano, anchoColumna) <= altoRenglon - HOLGURA) {
                return tamano;
            }
        }
        return tamanos[tamanos.length - 1];
    }

    /** Lo que ocupan el rótulo y el texto partido en las líneas que le hagan falta, en puntos. */
    static double altoNecesario(String texto, int tamano, double anchoColumna) {
        double anchoUtil = anchoColumna - 2 * MARGEN_CELDA;
        double ancho = anchoTexto(texto, tamano);
        int lineas = ancho <= anchoUtil ? 1 : (int) Math.ceil(ancho * PERDIDA_AL_PARTIR / anchoUtil);
        return altoLinea(TAMANO_ROTULO) + lineas * altoLinea(tamano);
    }

    /** El mayor tamaño con el que el texto cabe en una línea de ese ancho. */
    static int tamanoEnUnaLinea(String texto, double ancho, int[] tamanos) {
        for (int tamano : tamanos) {
            if (anchoTexto(texto, tamano) <= ancho) {
                return tamano;
            }
        }
        return tamanos[tamanos.length - 1];
    }

    /** Lo que mide el texto en puntos, letra a letra. */
    private static double anchoTexto(String texto, int tamano) {
        double cuerpos = 0;
        for (char letra : (texto == null ? "" : texto).toCharArray()) {
            cuerpos += Character.isWhitespace(letra) ? ANCHO_ESPACIO
                    : Character.isLowerCase(letra) ? ANCHO_MINUSCULA : ANCHO_MAYUSCULA;
        }
        return Math.max(ANCHO_MAYUSCULA, cuerpos) * tamano;
    }

    private static double altoLinea(int tamano) {
        return Math.ceil(tamano * INTERLINEADO);
    }

    private static double puntos(double centimetros) {
        return centimetros * PUNTOS_POR_CM;
    }

    private static double suma(float[] altos) {
        double total = 0;
        for (float alto : altos) {
            total += alto;
        }
        return total;
    }

    /**
     * El ancho de columna en las unidades de Excel (1/256 de carácter, a
     * 7 píxeles el carácter de Calibri 11 y 96 píxeles por pulgada).
     */
    static int anchoColumna(double centimetros) {
        double pixeles = centimetros / 2.54 * 96;
        return (int) Math.round(pixeles / 7 * 256);
    }

    /** Fuentes y estilos del libro, creados una vez: Excel tiene un tope de unos y otros. */
    private static final class Estilos {

        private final XSSFWorkbook libro;
        private final Map<String, XSSFFont> fuentes = new HashMap<>();
        final XSSFCellStyle derecha;
        final XSSFCellStyle nombrePiel;
        final XSSFCellStyle colorPiel;
        final XSSFCellStyle recuadro;

        Estilos(XSSFWorkbook libro) {
            this.libro = libro;
            derecha = libro.createCellStyle();
            derecha.setAlignment(HorizontalAlignment.RIGHT);
            derecha.setVerticalAlignment(VerticalAlignment.CENTER);

            nombrePiel = libro.createCellStyle();
            nombrePiel.setAlignment(HorizontalAlignment.RIGHT);
            nombrePiel.setVerticalAlignment(VerticalAlignment.CENTER);
            nombrePiel.setWrapText(true);

            colorPiel = libro.createCellStyle();
            colorPiel.setAlignment(HorizontalAlignment.CENTER);
            colorPiel.setVerticalAlignment(VerticalAlignment.CENTER);
            colorPiel.setWrapText(true);

            recuadro = libro.createCellStyle();
            recuadro.setBorderTop(BorderStyle.THIN);
            recuadro.setBorderBottom(BorderStyle.THIN);
            recuadro.setBorderLeft(BorderStyle.THIN);
            recuadro.setBorderRight(BorderStyle.THIN);
        }

        XSSFRichTextString texto(String texto, int tamano, boolean negrita, byte[] color) {
            XSSFRichTextString rico = new XSSFRichTextString(texto);
            rico.applyFont(fuente(tamano, negrita, color));
            return rico;
        }

        /** "Color  LZZ NOIR": el rótulo pequeño y en gris, el valor grande y en negrita. */
        XSSFRichTextString rotuloYValor(String rotulo, int tamanoRotulo, String valor, int tamanoValor) {
            XSSFRichTextString rico = new XSSFRichTextString(rotulo + valor);
            rico.applyFont(0, rotulo.length(), fuente(tamanoRotulo, false, GRIS));
            if (!valor.isEmpty()) {
                rico.applyFont(rotulo.length(), rotulo.length() + valor.length(),
                        fuente(tamanoValor, true, NEGRO));
            }
            return rico;
        }

        /** "PIEL" a 14 en gris y, en la línea de abajo, el nombre en negrita. */
        XSSFRichTextString rotuloEncimaDeValor(String rotulo, String valor, int tamanoValor) {
            String texto = valor.isEmpty() ? rotulo : rotulo + "\n" + valor;
            XSSFRichTextString rico = new XSSFRichTextString(texto);
            rico.applyFont(0, rotulo.length(), fuente(TAMANO_ROTULO, false, GRIS));
            if (!valor.isEmpty()) {
                rico.applyFont(rotulo.length(), texto.length(), fuente(tamanoValor, true, NEGRO));
            }
            return rico;
        }

        private XSSFFont fuente(int tamano, boolean negrita, byte[] color) {
            String clave = tamano + "|" + negrita + "|" + color[0];
            return fuentes.computeIfAbsent(clave, k -> {
                XSSFFont fuente = libro.createFont();
                fuente.setFontName(FUENTE);
                fuente.setFontHeightInPoints((short) tamano);
                fuente.setBold(negrita);
                fuente.setColor(new XSSFColor(color, null));
                return fuente;
            });
        }
    }
}

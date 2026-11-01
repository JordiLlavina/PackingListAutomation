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
 * La ficha es la del Word que el usuario retocó a mano: arriba, hasta tres
 * fotos del modelo a la izquierda (ver HUECOS_FOTOS) y a la derecha cliente y
 * temporada en pequeño, y en grande el modelo, el color y la cantidad. Debajo,
 * las pieles —principal, de combinación y forro—, una por renglón: el nombre a
 * la derecha de la primera columna, su COLOR en la segunda y un recuadro
 * vacío en la tercera, donde se pega la muestra de piel una vez impresa. Una
 * piel sola va al pie de la página.
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

    /**
     * Banda superior de 7 cm: fotos a la izquierda y, a la derecha, cliente y
     * temporada, modelo, nombre del bolso, color y cantidad. La fila del
     * nombre salió del aire de debajo de la cantidad (34,4 → 10,4), para que
     * la banda mida lo mismo y las fotos sigan donde el usuario las colocó.
     */
    static final float[] FILAS_BANDA = {20, 52, 24, 40, 52, 10.4f};
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

    /**
     * Dónde van las fotos de la banda, según cuántas son: de {@code x0} a
     * {@code x1} y de {@code y0} a {@code y1}, en puntos desde la esquina de
     * la ficha, partido en tantos huecos iguales como fotos, con
     * {@code separacion} entre uno y otro. Cada foto se encaja en su hueco
     * sin deformarla y centrada.
     */
    record HuecoFotos(double x0, double x1, double y0, double y1, double separacion) {
    }

    /**
     * Copiados de las fichas que el usuario colocó a mano en el ejemplo
     * ("ejemplos de varias fotos corte.xlsx"). Una: en la columna de la
     * izquierda, como siempre. Dos: una al lado de la otra, del mismo alto,
     * hasta debajo del texto de la derecha. Tres: más pequeñas, en fila y
     * empezando debajo de la línea de cliente y temporada, que si no
     * taparían; la tercera llega hasta la columna del recuadro.
     */
    static final HuecoFotos[] HUECOS_FOTOS = {
            null,
            new HuecoFotos(AIRE_FOTO, ANCHO_NOMBRE_CM * PUNTOS_POR_CM - AIRE_FOTO,
                    AIRE_FOTO, ALTO_BANDA - AIRE_FOTO, 0),
            new HuecoFotos(40.5, 448.6, AIRE_FOTO, ALTO_BANDA - AIRE_FOTO, 15),
            new HuecoFotos(17.7, 587.6, FILAS_BANDA[0], ALTO_BANDA - AIRE_FOTO, 9)};

    /** Lo que mide la ficha de ancho, sus tres columnas, en puntos. */
    static final double ANCHO_FICHA = (ANCHO_NOMBRE_CM + ANCHO_COLOR_CM + ANCHO_RECUADRO_CM) * PUNTOS_POR_CM;

    /** Aire entre la última foto y el texto de la derecha, que se encoge para no quedar debajo. */
    private static final double AIRE_DATOS = 9;

    private static final int[] TAMANOS_CLIENTE = {12, 11, 10, 9};
    private static final int[] TAMANOS_MODELO = {40, 36, 32, 28, 24, 20};
    private static final int[] TAMANOS_NOMBRE_MODELO = {16, 14, 12, 11, 10};
    private static final int[] TAMANOS_CANTIDAD = {40, 36, 32, 28, 24, 20};

    private static final int COL_NOMBRE = 0;
    private static final int COL_COLOR = 1;
    private static final int COL_RECUADRO = 2;

    /** Al final del nombre de la hoja de una orden de prensa, por decisión del usuario. */
    static final String SUFIJO_PRENSA = " PRENSA";

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
                String nombre = nombreUnico(nombreHoja(orden), orden.prensa() ? SUFIJO_PRENSA : "", usados);
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
        return nombreUnico(deseado, "", usados);
    }

    /**
     * {@code fin} va siempre al final y entero (" PRENSA"): lo que se
     * recorta para que quepa es lo de delante, y el " (2)" va antes de él.
     */
    static String nombreUnico(String deseado, String fin, Set<String> usados) {
        int sitio = MAXIMO_NOMBRE_HOJA - fin.length();
        String nombre = recortar(deseado.replaceAll(PROHIBIDOS_EN_HOJA, "-").replaceAll("\\s+", " ")
                .trim().replaceAll("^'+|'+$", ""), sitio);
        if (nombre.isEmpty()) {
            nombre = "Orden";
        }
        String unico = nombre + fin;
        for (int intento = 2; usados.contains(unico.toLowerCase(Locale.ROOT)); intento++) {
            String sufijo = " (" + intento + ")";
            unico = recortar(nombre, sitio - sufijo.length()) + sufijo + fin;
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
        String cliente = orden.cliente().toUpperCase(Locale.ROOT) + " | Temporada " + orden.temporada();
        String cantidad = String.valueOf(orden.bolsos());
        // En mayúsculas, como el MODÈLE del packing de APC: el excel de
        // producción ya lo escribe así y la Désignation del pedido no.
        String nombreBolso = orden.nombreModelo().toUpperCase(Locale.ROOT);
        // El texto va alineado a la derecha y nunca pasa por debajo de las
        // fotos: con dos o tres, que entran en su columna, primero se encoge,
        // y si ni con la letra más pequeña cabe, las fotos se estrechan.
        double necesario = Math.max(Math.max(anchoTexto(orden.modelo(), ultimo(TAMANOS_MODELO)),
                        nombreBolso.isEmpty() ? 0 : anchoTexto(nombreBolso, ultimo(TAMANOS_NOMBRE_MODELO))), Math.max(
                anchoTexto("Color  ", 12) + anchoTexto(orden.color(), ultimo(TAMANOS_COLOR_MODELO)),
                anchoTexto("Cantidad  ", 12) + anchoTexto(cantidad, ultimo(TAMANOS_CANTIDAD))));
        // Sin foto el hueco se queda en blanco: un rótulo ahí no le dice nada al
        // cortador. El punto de más es para que un empate de decimales no deje
        // el texto una milésima más ancho que su sitio y lo encoja de más.
        Colocacion colocacion = fotos(libro, dibujo, hoja, inicio, orden.fotos(),
                ANCHO_FICHA - 2 * MARGEN_CELDA - necesario - AIRE_DATOS - 1, fotos);
        double anchoDatos = anchoDatos(colocacion.fin());
        // La línea de cliente y temporada solo se encoge si alguna foto llega a su altura.
        double anchoCliente = colocacion.arriba() >= FILAS_BANDA[0] ? anchoDatos(0) : anchoDatos;
        datoDeBanda(hoja, inicio, estilos.texto(cliente,
                tamanoEnUnaLinea(cliente, anchoCliente, TAMANOS_CLIENTE), false, GRIS), estilos.derecha);
        datoDeBanda(hoja, inicio + 1, estilos.texto(orden.modelo(),
                tamanoEnUnaLinea(orden.modelo(), anchoDatos, TAMANOS_MODELO), true, NEGRO), estilos.derecha);
        // Sin nombre (el pedido de AMI no lo trae) la fila se queda vacía: la
        // banda mide siempre lo mismo y las fotos no se mueven.
        if (!nombreBolso.isEmpty()) {
            datoDeBanda(hoja, inicio + 2, estilos.texto(nombreBolso,
                    tamanoEnUnaLinea(nombreBolso, anchoDatos, TAMANOS_NOMBRE_MODELO), false, NEGRO), estilos.derecha);
        }
        datoDeBanda(hoja, inicio + 3, estilos.rotuloYValor("Color  ", 12, orden.color(),
                tamanoEnUnaLinea(orden.color(), anchoDatos - anchoTexto("Color  ", 12),
                        TAMANOS_COLOR_MODELO)), estilos.derecha);
        datoDeBanda(hoja, inicio + 4, estilos.rotuloYValor("Cantidad  ", 12, cantidad,
                tamanoEnUnaLinea(cantidad, anchoDatos - anchoTexto("Cantidad  ", 12), TAMANOS_CANTIDAD)),
                estilos.derecha);

        int fila = inicio + FILAS_BANDA.length;
        List<String[]> renglones = renglones(orden.pieles());
        float alto = altoPiel(renglones.size());
        // Una piel sola va al pie del A4, por decisión del usuario: arriba
        // dejaba media página en blanco debajo del recuadro.
        hoja.createRow(fila++).setHeightInPoints(separacion(renglones.size(), alto));
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

    /** Dónde han quedado las fotos: dónde acaba la última por la derecha y dónde empieza la más alta. */
    record Colocacion(double fin, double arriba) {
        static final Colocacion NINGUNA = new Colocacion(0, Double.MAX_VALUE);
    }

    /**
     * Las fotos de la banda, cada una encajada en su hueco (ver
     * {@link #HUECOS_FOTOS}) sin deformarla —manda el lado que antes llega al
     * borde— y centrada en él. {@code limiteDerecho}: hasta dónde pueden
     * llegar sin tapar el texto de la derecha; si el hueco del ejemplo se
     * pasa, se estrecha, aunque nunca por debajo de la columna de la
     * izquierda. Una foto que sale en varias fichas se guarda una sola vez en
     * el libro.
     */
    private static Colocacion fotos(XSSFWorkbook libro, XSSFDrawing dibujo, XSSFSheet hoja, int inicio,
                                    List<Imagen> imagenes, double limiteDerecho, Map<Imagen, Integer> fotos) {
        int cuantas = Math.min(imagenes.size(), OrdenCorte.MAXIMO_FOTOS);
        if (cuantas == 0) {
            return Colocacion.NINGUNA;
        }
        HuecoFotos hueco = HUECOS_FOTOS[cuantas];
        double minimo = Math.min(hueco.x1(), puntos(ANCHO_NOMBRE_CM) - AIRE_FOTO);
        double x1 = Math.max(minimo, Math.min(hueco.x1(), limiteDerecho));
        double anchoHueco = (x1 - hueco.x0() - (cuantas - 1) * hueco.separacion()) / cuantas;
        double altoHueco = hueco.y1() - hueco.y0();
        double fin = 0;
        double arriba = Double.MAX_VALUE;
        for (int i = 0; i < cuantas; i++) {
            Imagen imagen = imagenes.get(i);
            double escala = Math.min(anchoHueco / imagen.ancho(), altoHueco / imagen.alto());
            double ancho = imagen.ancho() * escala;
            double alto = imagen.alto() * escala;
            double x = hueco.x0() + i * (anchoHueco + hueco.separacion()) + (anchoHueco - ancho) / 2;
            double y = hueco.y0() + (altoHueco - alto) / 2;
            int indice = fotos.computeIfAbsent(imagen,
                    foto -> libro.addPicture(foto.jpeg(), Workbook.PICTURE_TYPE_JPEG));

            // Cada esquina con su columna: una foto que pasa a la siguiente
            // columna no puede anclarse con un desplazamiento más ancho que la
            // suya, que Excel no respeta.
            int[] izquierda = columnaYResto(x);
            int[] derecha = columnaYResto(x + ancho);
            int[] desde = filaYResto(hoja, inicio, y);
            int[] hasta = filaYResto(hoja, inicio, y + alto);
            XSSFClientAnchor ancla = new XSSFClientAnchor(izquierda[1], desde[1], derecha[1], hasta[1],
                    izquierda[0], desde[0], derecha[0], hasta[0]);
            ancla.setAnchorType(XSSFClientAnchor.AnchorType.MOVE_AND_RESIZE);
            dibujo.createPicture(ancla, indice);
            fin = Math.max(fin, x + ancho);
            arriba = Math.min(arriba, y);
        }
        return new Colocacion(fin, arriba);
    }

    private static int ultimo(int[] tamanos) {
        return tamanos[tamanos.length - 1];
    }

    /**
     * Lo que queda para el texto de la derecha de la banda: desde lo que
     * acabe más a la derecha —el principio de su columna o la última foto,
     * con aire— hasta el borde de la ficha.
     */
    static double anchoDatos(double finFotos) {
        double desde = Math.max(puntos(ANCHO_NOMBRE_CM), finFotos > 0 ? finFotos + AIRE_DATOS : 0);
        return ANCHO_FICHA - desde - 2 * MARGEN_CELDA;
    }

    /** La columna donde cae una distancia en puntos desde la izquierda y lo que sobra, en EMU. */
    private static int[] columnaYResto(double puntos) {
        double[] anchos = {puntos(ANCHO_NOMBRE_CM), puntos(ANCHO_COLOR_CM), puntos(ANCHO_RECUADRO_CM)};
        int columna = 0;
        double restante = puntos;
        while (columna < anchos.length - 1 && restante > anchos[columna]) {
            restante -= anchos[columna];
            columna++;
        }
        return new int[] {COL_NOMBRE + columna, (int) Math.round(Math.max(0, restante) * Units.EMU_PER_POINT)};
    }

    /**
     * El aire entre la banda y la primera piel. Una piel sola baja al pie de
     * la página: el aire se queda con todo lo que ella no ocupa.
     */
    static float separacion(int pieles, float altoPiel) {
        if (pieles != 1) {
            return SEPARACION;
        }
        return (float) Math.floor(ALTO_UTIL - ALTO_BANDA - RESERVA - altoPiel);
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
    static double anchoTexto(String texto, int tamano) {
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

package com.puntotres.packinglist.service.embalaje;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.DatosHojaEmbalaje;
import com.puntotres.packinglist.model.DatosHojaEmbalaje.DestinacionHoja;

/**
 * Escribe la <b>hoja de instrucciones de embalaje</b>: un A4 en Word que se
 * pega encima del sobre con las etiquetas de un modelo y le dice al taller
 * cómo empaquetarlo (caja, unidades por caja, bolsa de tela, etiqueta de
 * cartón interior, a qué destinaciones va y con qué pedido).
 *
 * Se lee a un metro de distancia, así que manda el aire y el tamaño de la
 * letra: pocos elementos, la jerarquía solo por tamaño y peso, una sola
 * familia (Calibri) y todo en negros y grises, sin ningún color. Arriba, la
 * cabecera: a la izquierda el título (cliente · temporada · taller), el
 * artículo y las tres fichas del envío; a la derecha el QR. Debajo, la foto
 * con el total y el embalaje al lado; luego las destinaciones, el reparto y
 * los comentarios. Solo «Comentarios» lleva título de sección: los demás
 * bloques se separan con aire, sin filetes (decisión del usuario sobre la
 * hoja impresa).
 *
 * Lo que no tiene nada que decir ni se pinta: ni el QR sin enlace, ni el
 * reparto o los comentarios sin texto, ni la fila de la instrucción especial.
 * La foto es la excepción: sin ella su hueco se conserva en blanco, para que
 * el bloque de al lado no cambie de sitio de una hoja a otra. Si no cabe en
 * un A4, Word sigue en la hoja siguiente; no se recorta nada. Sin plantilla
 * {@code .docx}: la maquetación son las constantes de esta clase.
 */
@Service
public class InfoEmbalajeDocBuilder {

    /** Sustituye lo que no vale en el nombre de un fichero; es el patrón de las etiquetas de artículo. */
    private static final Pattern CARACTERES_INSEGUROS = Pattern.compile("[\\\\/:*?\"<>|]+");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * A4 vertical en veinteavos de punto. Márgenes laterales de 1,5 cm; los de
     * arriba y abajo, de 1 cm: la hoja con todo lo que puede llevar (seis
     * destinaciones, ocho líneas de reparto y cuatro de comentarios) no cabía
     * en un A4 con 1,5, y esos 2 cm de papel es lo único que se puede ceder
     * sin bajar las letras del tamaño que se lee a un metro.
     */
    private static final int ANCHO_A4 = 11906;
    private static final int ALTO_A4 = 16838;
    private static final int MARGEN = 850;
    private static final int MARGEN_VERTICAL = 567;
    private static final int ANCHO_UTIL = ANCHO_A4 - 2 * MARGEN;

    private static final String FUENTE = "Calibri";

    /** Paleta: solo negros y grises. Nada de color, por decisión del usuario. */
    private static final String NEGRO = "000000";
    /** Texto secundario: la línea del color, el nombre del bolso. */
    private static final String GRIS_OSCURO = "404040";
    /** Rótulos pequeños: los de las fichas, las filas del embalaje y la cabecera de la tabla. */
    private static final String GRIS_ROTULO = "808080";
    /** Rellenos: las fichas, la cabecera de la tabla y el recuadro de comentarios. */
    private static final String GRIS_FONDO = "F2F2F2";
    /** Filetes y bordes de recuadro. */
    private static final String GRIS_FILETE = "BFBFBF";
    private static final String BLANCO = "FFFFFF";

    // Tamaños de letra, en puntos.
    private static final int TAMANO_TITULO = 22;
    private static final int TAMANO_TITULO_QR = 9;
    private static final int TAMANO_ROTULO_FICHA = 8;
    private static final int TAMANO_VALOR_FICHA = 20;
    private static final int TAMANO_REFERENCIA = 26;
    private static final int TAMANO_COLOR = 14;
    private static final int TAMANO_NOMBRE = 12;
    private static final int TAMANO_TOTAL = 16;
    private static final int TAMANO_SECCION = 11;
    private static final int TAMANO_ROTULO_FILA = 10;
    private static final int TAMANO_VALOR_FILA = 12;
    private static final int TAMANO_INSTRUCCION = 13;
    private static final int TAMANO_TABLA = 11;
    private static final int TAMANO_TOTAL_TABLA = 14;
    private static final int TAMANO_CABECERA_TABLA = 9;
    private static final int TAMANO_REPARTO = 11;
    private static final int TAMANO_COMENTARIOS = 12;

    // Filetes en octavos de punto.
    private static final int FILETE_FINO = 4;
    private static final int FILETE_TOTAL = 12;
    private static final int FILETE_QR = 12;
    private static final int FILETE_COMENTARIOS = 8;

    /** Medidas de las imágenes. Un centímetro son 360.000 EMU, la unidad de Word. */
    private static final int EMU_POR_CM = 360_000;
    private static final int TWIPS_POR_CM = 567;
    private static final double ANCHO_QR_CM = 3.5;
    private static final double ANCHO_FOTO_CM = 5;
    private static final double ALTO_MAXIMO_FOTO_CM = 4;
    /**
     * Píxeles del QR: 3,5 cm a unos 300 ppp. Se pega 1:1, sin que Word ni
     * nadie lo reescale, porque un QR reescalado con interpolación se
     * emborrona y deja de leerse con el móvil.
     */
    private static final int PIXELES_QR = 413;

    /** Aire entre bloques, en puntos: es lo único que los separa, no hay filetes. */
    private static final int ESPACIO_BLOQUES_PT = 12;
    /** Aire de la cabecera, en veinteavos: entre el título y el artículo, y entre el artículo y las fichas. */
    private static final int ESPACIO_TRAS_TITULO = 160;
    private static final int ESPACIO_ANTES_FICHAS = 200;
    private static final int ESPACIO_COMENTARIOS_DESPUES = 60;
    /** Alto de línea del reparto: la letra de 11 pt con un punto y medio de aire, menos que el automático. */
    private static final double ALTO_LINEA_REPARTO_PT = 12.5;
    private static final int MARGEN_CELDA_FICHA = 50;
    private static final int MARGEN_CELDA_FILA = 10;
    /**
     * Aire de arriba y abajo de cada fila de destinación, en veinteavos: 40
     * más por lado que la primera versión, que el usuario quería algo más alta.
     */
    private static final int MARGEN_VERTICAL_TABLA = 50;
    private static final int MARGEN_CELDA_COMENTARIOS = 70;
    /** Separación entre la foto y el bloque de al lado. */
    private static final int HUECO_FOTO = 340;
    private static final int ANCHO_COLUMNA_QR = 2900;
    /** Separación entre el bloque de la izquierda de la cabecera y el recuadro del QR. */
    private static final int HUECO_QR = 280;
    private static final int ANCHO_ROTULO_FILA = 2700;
    private static final int[] ANCHOS_DESTINACIONES = {4400, 3400, 2506};

    private static final String[] CABECERAS_DESTINACIONES = {"Destinación", "Nº pedido", "Cantidad"};
    private static final String SEPARADOR_TITULO = " · ";

    /**
     * Nombre del fichero: {@code Info embalaje <cliente> <nº envío> <referencia> <color>.docx},
     * sin los caracteres que Windows no admite en un nombre. Las partes que
     * faltan se saltan para no dejar dobles espacios.
     */
    public static String nombreFichero(DatosHojaEmbalaje datos) {
        List<String> partes = new ArrayList<>();
        partes.add("Info embalaje");
        for (String parte : new String[] {datos.nombreCliente(), String.valueOf(datos.numeroEnvio()),
                datos.referencia(), datos.codigoColor()}) {
            if (!parte.isBlank()) {
                partes.add(parte.trim());
            }
        }
        String nombre = String.join(" ", partes) + ".docx";
        return CARACTERES_INSEGUROS.matcher(nombre).replaceAll("_").replaceAll("\\s+", " ");
    }

    /**
     * El título de la hoja: cliente · temporada · taller, saltando los que
     * faltan para no dejar separadores sueltos.
     */
    public static String titulo(DatosHojaEmbalaje datos) {
        List<String> partes = new ArrayList<>();
        for (String dato : new String[] {nombreDelCliente(datos), datos.temporada(), datos.taller()}) {
            if (dato != null && !dato.isBlank()) {
                partes.add(dato.trim());
            }
        }
        return String.join(SEPARADOR_TITULO, partes);
    }

    /**
     * El Word de la hoja. {@code fotoJpeg} es la foto principal del modelo en
     * el catálogo, o {@code null} si no tiene: su hueco se queda en blanco.
     */
    public byte[] generar(DatosHojaEmbalaje datos, byte[] fotoJpeg) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            configurarPagina(doc);
            escribirCabecera(doc, datos);
            espacio(doc, ESPACIO_BLOQUES_PT);
            escribirFotoYEmbalaje(doc, datos, fotoJpeg);
            espacio(doc, ESPACIO_BLOQUES_PT);
            escribirDestinaciones(doc, datos);
            escribirReparto(doc, datos);
            escribirComentarios(doc, datos);
            // Una tabla al final del cuerpo la deja Word sin párrafo detrás y
            // abre el documento "reparado" en algunas versiones: este lo cierra.
            espacio(doc, 1);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            doc.write(salida);
            return salida.toByteArray();
        }
    }

    private static void configurarPagina(XWPFDocument doc) {
        CTSectPr seccion = doc.getDocument().getBody().addNewSectPr();
        CTPageSz tamano = seccion.addNewPgSz();
        tamano.setW(BigInteger.valueOf(ANCHO_A4));
        tamano.setH(BigInteger.valueOf(ALTO_A4));
        CTPageMar margen = seccion.addNewPgMar();
        margen.setTop(BigInteger.valueOf(MARGEN_VERTICAL));
        margen.setBottom(BigInteger.valueOf(MARGEN_VERTICAL));
        margen.setLeft(BigInteger.valueOf(MARGEN));
        margen.setRight(BigInteger.valueOf(MARGEN));
    }

    // --- Los bloques de la hoja ---

    /**
     * La cabecera, en dos columnas. A la izquierda, de arriba abajo: el
     * título (cliente · temporada · taller), el artículo (referencia, color y
     * nombre del bolso) y las tres fichas del envío. A la derecha, solo si hay
     * enlace, el recuadro con el QR de la guía; sin enlace la columna se queda
     * vacía y el bloque de la izquierda no se ensancha, para que la hoja se
     * vea igual con QR o sin él.
     */
    private void escribirCabecera(XWPFDocument doc, DatosHojaEmbalaje datos) throws IOException {
        int anchoIzquierda = ANCHO_UTIL - ANCHO_COLUMNA_QR;
        XWPFTable tabla = nuevaTabla(doc, 1, anchoIzquierda, ANCHO_COLUMNA_QR);
        XWPFTableCell izquierda = tabla.getRow(0).getCell(0);
        texto(parrafo(izquierda, ParagraphAlignment.LEFT), titulo(datos), TAMANO_TITULO, true, NEGRO);

        XWPFParagraph referencia = parrafo(izquierda, ParagraphAlignment.LEFT);
        referencia.setSpacingBefore(ESPACIO_TRAS_TITULO);
        texto(referencia, datos.referencia(), TAMANO_REFERENCIA, true, NEGRO);
        String color = (datos.codigoColor() + " " + datos.descripcionColor()).trim();
        if (!color.isEmpty()) {
            texto(parrafo(izquierda, ParagraphAlignment.LEFT), color, TAMANO_COLOR, false, GRIS_OSCURO);
        }
        if (!datos.nombreBolso().isBlank()) {
            XWPFParagraph nombre = parrafo(izquierda, ParagraphAlignment.LEFT);
            nombre.setSpacingBefore(40);
            texto(nombre, datos.nombreBolso().toUpperCase(), TAMANO_NOMBRE, false, GRIS_OSCURO);
        }
        espacioEnCelda(izquierda, ESPACIO_ANTES_FICHAS);
        escribirFichasDeEnvio(izquierda, anchoIzquierda - HUECO_QR, datos);

        if (datos.tieneEnlace()) {
            XWPFTableCell recuadro = tabla.getRow(0).getCell(1);
            bordes(recuadro, FILETE_QR, GRIS_OSCURO);
            XWPFParagraph titulo = parrafo(recuadro, ParagraphAlignment.CENTER);
            titulo.setSpacingBefore(40);
            titulo.setSpacingAfter(30);
            XWPFRun run = texto(titulo, "Guide d'emballage du sac", TAMANO_TITULO_QR, true, NEGRO);
            run.setSmallCaps(true);
            XWPFParagraph qr = parrafo(recuadro, ParagraphAlignment.CENTER);
            imagen(qr, CodigoQr.png(datos.enlaceVideo().trim(), PIXELES_QR), PictureType.PNG,
                    "qr.png", ANCHO_QR_CM, ANCHO_QR_CM);
        }
    }

    /**
     * Tres fichas en fila, Nº envío, nota del ERP y fecha, con un hueco blanco
     * entre ellas. Van en una tabla dentro de la celda de la cabecera, debajo
     * del artículo, para que el QR quede al lado de todo el bloque.
     */
    private void escribirFichasDeEnvio(XWPFTableCell celda, int anchoTotal, DatosHojaEmbalaje datos) {
        int ancho = anchoTotal / 3;
        XWPFTable tabla = tablaEnCelda(celda, 1, ancho, ancho, anchoTotal - 2 * ancho);
        tabla.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 48, 0, BLANCO);
        tabla.setCellMargins(MARGEN_CELDA_FICHA, 160, MARGEN_CELDA_FICHA, 160);
        String[] rotulos = {"Nº ENVÍO", "NOTA ICSUITE", "FECHA"};
        String[] valores = {String.valueOf(datos.numeroEnvio()), datos.nota(),
                datos.fecha() == null ? "" : datos.fecha().format(FORMATO_FECHA)};
        for (int i = 0; i < rotulos.length; i++) {
            XWPFTableCell ficha = tabla.getRow(0).getCell(i);
            ficha.setColor(GRIS_FONDO);
            texto(parrafo(ficha, ParagraphAlignment.LEFT), rotulos[i], TAMANO_ROTULO_FICHA, false, GRIS_ROTULO);
            texto(parrafo(ficha, ParagraphAlignment.LEFT), valores[i], TAMANO_VALOR_FICHA, true, NEGRO);
        }
    }

    /**
     * La foto a la izquierda y, a la derecha, el total de unidades y el
     * embalaje (caja, unidades por caja, bolsa, etiqueta interior e
     * instrucción especial). La foto va a 5 cm de ancho conservando su
     * proporción: se lee la medida del fichero en vez de forzar un alto, o un
     * bolso saldría achatado. Si es vertical y pasaría de 4 cm de alto se
     * reduce entera: una foto de móvil en vertical a 5 cm de ancho mide casi 7
     * de alto y empuja los comentarios a una segunda hoja, que es justo lo que
     * esta hoja no puede hacer. Sin foto la celda conserva su ancho y se queda
     * en blanco.
     */
    private void escribirFotoYEmbalaje(XWPFDocument doc, DatosHojaEmbalaje datos, byte[] fotoJpeg)
            throws IOException {
        int anchoFoto = (int) Math.round(ANCHO_FOTO_CM * TWIPS_POR_CM) + HUECO_FOTO;
        int anchoDerecha = ANCHO_UTIL - anchoFoto;
        XWPFTable tabla = nuevaTabla(doc, 1, anchoFoto, anchoDerecha);
        XWPFTableCell celdaFoto = tabla.getRow(0).getCell(0);
        XWPFParagraph parrafoFoto = parrafo(celdaFoto, ParagraphAlignment.LEFT);
        double[] medida = medidaFoto(fotoJpeg);
        if (medida != null) {
            double ancho = ANCHO_FOTO_CM;
            double alto = ancho * medida[1] / medida[0];
            if (alto > ALTO_MAXIMO_FOTO_CM) {
                alto = ALTO_MAXIMO_FOTO_CM;
                ancho = alto * medida[0] / medida[1];
            }
            imagen(parrafoFoto, fotoJpeg, PictureType.JPEG, "foto.jpg", ancho, alto);
        }

        XWPFTableCell derecha = tabla.getRow(0).getCell(1);
        XWPFParagraph total = parrafo(derecha, ParagraphAlignment.LEFT);
        total.setSpacingAfter(120);
        texto(total, "TOTAL " + datos.totalUnidades() + " UNIDADES", TAMANO_TOTAL, true, NEGRO);
        escribirEmbalaje(derecha, anchoDerecha, datos);
    }

    /** Rótulo y valor de la caja, la bolsa, la etiqueta interior y, destacada, la instrucción especial. */
    private void escribirEmbalaje(XWPFTableCell celda, int anchoTotal, DatosHojaEmbalaje datos) {
        List<String[]> filas = new ArrayList<>();
        filas.add(new String[] {"Caja", datos.medidaCaja()});
        filas.add(new String[] {"Unidades por caja",
                datos.unidadesPorCaja() == null ? "" : String.valueOf(datos.unidadesPorCaja())});
        filas.add(new String[] {"Bolsa de tela", datos.bolsaTela()});
        filas.add(new String[] {"Etiqueta cartón interior", fraseEtiquetaInterior(datos.etiquetaInterior())});
        if (!datos.instruccionEspecial().isBlank()) {
            filas.add(new String[] {"Instrucción especial", datos.instruccionEspecial()});
        }

        XWPFTable tabla = tablaEnCelda(celda, filas.size(), ANCHO_ROTULO_FILA, anchoTotal - ANCHO_ROTULO_FILA);
        tabla.setCellMargins(MARGEN_CELDA_FILA, 0, MARGEN_CELDA_FILA, 60);
        for (int i = 0; i < filas.size(); i++) {
            boolean instruccion = filas.get(i)[0].startsWith("Instrucción");
            XWPFTableRow fila = tabla.getRow(i);
            texto(parrafo(fila.getCell(0), ParagraphAlignment.LEFT), filas.get(i)[0],
                    TAMANO_ROTULO_FILA, false, GRIS_ROTULO);
            texto(parrafo(fila.getCell(1), ParagraphAlignment.LEFT), filas.get(i)[1],
                    instruccion ? TAMANO_INSTRUCCION : TAMANO_VALOR_FILA, true, NEGRO);
        }
    }

    /**
     * Cabecera gris, un filete fino entre filas, la cantidad a la derecha y el
     * total más grande, con un filete negro encima: es la cifra que el taller
     * tiene que cuadrar con las cajas.
     */
    private void escribirDestinaciones(XWPFDocument doc, DatosHojaEmbalaje datos) {
        List<DestinacionHoja> destinaciones = datos.destinaciones();
        XWPFTable tabla = nuevaTabla(doc, destinaciones.size() + 2, ANCHOS_DESTINACIONES);
        tabla.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, FILETE_FINO, 0, GRIS_FILETE);
        tabla.setCellMargins(MARGEN_VERTICAL_TABLA, 100, MARGEN_VERTICAL_TABLA, 100);

        for (int i = 0; i < CABECERAS_DESTINACIONES.length; i++) {
            XWPFTableCell celda = tabla.getRow(0).getCell(i);
            celda.setColor(GRIS_FONDO);
            texto(parrafo(celda, alineacionDeColumna(i)), CABECERAS_DESTINACIONES[i],
                    TAMANO_CABECERA_TABLA, true, GRIS_ROTULO);
        }
        for (int i = 0; i < destinaciones.size(); i++) {
            DestinacionHoja destinacion = destinaciones.get(i);
            filaDestinacion(tabla.getRow(i + 1), TAMANO_TABLA, false, destinacion.destino(), destinacion.pedido(),
                    String.valueOf(destinacion.cantidad()));
        }
        XWPFTableRow total = tabla.getRow(destinaciones.size() + 1);
        filaDestinacion(total, TAMANO_TOTAL_TABLA, true, "Total", "", String.valueOf(datos.totalUnidades()));
        for (XWPFTableCell celda : total.getTableCells()) {
            borde(celda, true, FILETE_TOTAL, NEGRO);
        }
    }

    private void filaDestinacion(XWPFTableRow fila, int tamano, boolean negrita, String... valores) {
        for (int i = 0; i < valores.length; i++) {
            texto(parrafo(fila.getCell(i), alineacionDeColumna(i)), valores[i], tamano, negrita, NEGRO);
        }
    }

    /** La cantidad (última columna) a la derecha para que las cifras se sumen a ojo. */
    private static ParagraphAlignment alineacionDeColumna(int columna) {
        return columna == CABECERAS_DESTINACIONES.length - 1 ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT;
    }

    /** El texto libre del reparto, una línea por párrafo, sin título. Sin texto no sale nada. */
    private void escribirReparto(XWPFDocument doc, DatosHojaEmbalaje datos) {
        if (datos.repartoCajas().isBlank()) {
            return;
        }
        espacio(doc, ESPACIO_BLOQUES_PT);
        for (String linea : lineas(datos.repartoCajas())) {
            XWPFParagraph parrafo = parrafo(doc.createParagraph(), ParagraphAlignment.LEFT);
            altoExacto(parrafo, ALTO_LINEA_REPARTO_PT);
            texto(parrafo, linea, TAMANO_REPARTO, false, NEGRO);
        }
    }

    /**
     * El único bloque con título, por decisión del usuario: un recuadro gris
     * con el texto en negrita. Sin texto no sale ni el título.
     */
    private void escribirComentarios(XWPFDocument doc, DatosHojaEmbalaje datos) {
        if (datos.comentarios().isBlank()) {
            return;
        }
        espacio(doc, ESPACIO_BLOQUES_PT);
        XWPFParagraph titulo = parrafo(doc.createParagraph(), ParagraphAlignment.LEFT);
        titulo.setSpacingAfter(ESPACIO_COMENTARIOS_DESPUES);
        titulo.setKeepNext(true);
        texto(titulo, "COMENTARIOS", TAMANO_SECCION, true, NEGRO);
        XWPFTable tabla = nuevaTabla(doc, 1, ANCHO_UTIL);
        tabla.setCellMargins(MARGEN_CELDA_COMENTARIOS, 180, MARGEN_CELDA_COMENTARIOS, 180);
        XWPFTableCell celda = tabla.getRow(0).getCell(0);
        celda.setColor(GRIS_FONDO);
        bordes(celda, FILETE_COMENTARIOS, GRIS_FILETE);
        for (String linea : lineas(datos.comentarios())) {
            texto(parrafo(celda, ParagraphAlignment.LEFT), linea, TAMANO_COMENTARIOS, true, NEGRO);
        }
    }

    // --- Datos ---

    /** La frase del taller: sin texto no lleva etiqueta; con él, el texto dice cuál (p. ej. PARIS). */
    private static String fraseEtiquetaInterior(String etiqueta) {
        return etiqueta.isBlank()
                ? "NO LLEVA ETIQUETA DE CARTÓN INTERIOR"
                : "LLEVA ETIQUETA DE CARTÓN INTERIOR " + etiqueta.trim();
    }

    private static String nombreDelCliente(DatosHojaEmbalaje datos) {
        return datos.nombreCliente().isBlank() ? datos.cliente() : datos.nombreCliente();
    }

    /** Las líneas de un texto libre, sin saltos de línea de Windows y sin renglones vacíos al final. */
    private static List<String> lineas(String texto) {
        List<String> lineas = new ArrayList<>(List.of(texto.stripTrailing().split("\\R", -1)));
        while (!lineas.isEmpty() && lineas.get(0).isBlank()) {
            lineas.remove(0);
        }
        return lineas;
    }

    /**
     * Ancho y alto en píxeles de la foto, o null si no se deja leer: sin
     * proporción no se puede colocar. Una foto rota o en un formato que Java no
     * lee (un JPEG corrupto, uno CMYK) se trata igual que «sin foto», con la
     * celda vacía: una foto mala del catálogo no puede dejar al taller sin hoja.
     */
    private static double[] medidaFoto(byte[] fotoJpeg) {
        if (fotoJpeg == null || fotoJpeg.length == 0) {
            return null;
        }
        try {
            var imagen = ImageIO.read(new ByteArrayInputStream(fotoJpeg));
            return imagen == null ? null : new double[] {imagen.getWidth(), imagen.getHeight()};
        } catch (IOException | RuntimeException ilegible) {
            return null;
        }
    }

    // --- Piezas de maquetación ---

    /** Un párrafo vacío de alto exacto: el aire entre dos tablas, que sin él Word fusiona. */
    private static void espacio(XWPFDocument doc, int puntos) {
        altoExacto(doc.createParagraph(), puntos);
    }

    /** Un párrafo vacío dentro de una celda, de alto exacto en veinteavos de punto. */
    private static void espacioEnCelda(XWPFTableCell celda, int veinteavos) {
        altoExacto(parrafo(celda, ParagraphAlignment.LEFT), veinteavos / 20.0);
    }

    /**
     * Fija el alto de la línea del párrafo, sin aire antes ni después. Solo
     * para texto: con una imagen en línea Word coloca la línea por la base y la
     * imagen se sale por arriba y tapa el párrafo anterior (el título del
     * recuadro del QR desaparecía así).
     */
    private static void altoExacto(XWPFParagraph parrafo, double puntos) {
        CTPPr propiedades = parrafo.getCTP().isSetPPr() ? parrafo.getCTP().getPPr() : parrafo.getCTP().addNewPPr();
        CTSpacing espaciado = propiedades.isSetSpacing() ? propiedades.getSpacing() : propiedades.addNewSpacing();
        espaciado.setBefore(BigInteger.ZERO);
        espaciado.setAfter(BigInteger.ZERO);
        espaciado.setLine(BigInteger.valueOf(Math.round(puntos * 20)));
        espaciado.setLineRule(STLineSpacingRule.EXACT);
    }

    /** Tabla del cuerpo del documento, con las medidas de {@link #medidasFijas}. */
    private static XWPFTable nuevaTabla(XWPFDocument doc, int filas, int... anchos) {
        return medidasFijas(doc.createTable(filas, anchos.length), anchos);
    }

    /**
     * Tabla dentro de una celda, al final de lo que ya lleva. Word exige que
     * una celda acabe en párrafo, así que detrás va uno de alto mínimo: sin
     * él abre el documento como dañado.
     */
    private static XWPFTable tablaEnCelda(XWPFTableCell celda, int filas, int... anchos) {
        XWPFTable tabla = new XWPFTable(celda.getCTTc().addNewTbl(), celda, filas, anchos.length);
        celda.insertTable(celda.getBodyElements().size(), tabla);
        altoExacto(sinEspaciado(celda.addParagraph(), ParagraphAlignment.LEFT), 1);
        return medidasFijas(tabla, anchos);
    }

    /**
     * Tabla de medidas fijas (la del grid y la de cada celda) y sin ningún
     * borde: cada bloque se dibuja luego los suyos. Con anchos "automáticos"
     * Word reparte las columnas por el contenido y el recuadro del QR o la
     * columna de la foto se moverían de una hoja a otra.
     */
    private static XWPFTable medidasFijas(XWPFTable tabla, int... anchos) {
        int total = 0;
        for (int ancho : anchos) {
            total += ancho;
        }
        CTTblPr propiedades = tabla.getCTTbl().getTblPr() != null ? tabla.getCTTbl().getTblPr()
                : tabla.getCTTbl().addNewTblPr();
        CTTblWidth anchoTabla = propiedades.isSetTblW() ? propiedades.getTblW() : propiedades.addNewTblW();
        anchoTabla.setType(STTblWidth.DXA);
        anchoTabla.setW(BigInteger.valueOf(total));
        if (propiedades.isSetTblLayout()) {
            propiedades.getTblLayout().setType(STTblLayoutType.FIXED);
        } else {
            propiedades.addNewTblLayout().setType(STTblLayoutType.FIXED);
        }

        CTTblGrid rejilla = tabla.getCTTbl().getTblGrid() != null ? tabla.getCTTbl().getTblGrid()
                : tabla.getCTTbl().addNewTblGrid();
        while (rejilla.sizeOfGridColArray() > 0) {
            rejilla.removeGridCol(0);
        }
        for (int ancho : anchos) {
            rejilla.addNewGridCol().setW(BigInteger.valueOf(ancho));
        }
        for (XWPFTableRow fila : tabla.getRows()) {
            fila.setCantSplitRow(true);
            for (int i = 0; i < anchos.length; i++) {
                CTTcPr celdaPr = fila.getCell(i).getCTTc().isSetTcPr()
                        ? fila.getCell(i).getCTTc().getTcPr() : fila.getCell(i).getCTTc().addNewTcPr();
                CTTblWidth anchoCelda = celdaPr.isSetTcW() ? celdaPr.getTcW() : celdaPr.addNewTcW();
                anchoCelda.setType(STTblWidth.DXA);
                anchoCelda.setW(BigInteger.valueOf(anchos[i]));
            }
        }
        tabla.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, NEGRO);
        tabla.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, NEGRO);
        tabla.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, NEGRO);
        tabla.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, NEGRO);
        tabla.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, NEGRO);
        tabla.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, NEGRO);
        tabla.setCellMargins(0, 0, 0, 0);
        return tabla;
    }

    /**
     * Un párrafo en la celda: el vacío que POI deja la primera vez y uno
     * nuevo cada vez después, sin espaciado (el aire lo ponen los márgenes
     * de celda y los espacios entre bloques).
     */
    private static XWPFParagraph parrafo(XWPFTableCell celda, ParagraphAlignment alineacion) {
        XWPFParagraph parrafo = celda.getBodyElements().size() == 1 && celda.getParagraphs().size() == 1
                && celda.getParagraphs().get(0).getRuns().isEmpty()
                ? celda.getParagraphs().get(0) : celda.addParagraph();
        return sinEspaciado(parrafo, alineacion);
    }

    private static XWPFParagraph parrafo(XWPFParagraph parrafo, ParagraphAlignment alineacion) {
        return sinEspaciado(parrafo, alineacion);
    }

    private static XWPFParagraph sinEspaciado(XWPFParagraph parrafo, ParagraphAlignment alineacion) {
        parrafo.setSpacingBefore(0);
        parrafo.setSpacingAfter(0);
        parrafo.setAlignment(alineacion);
        return parrafo;
    }

    private static XWPFRun texto(XWPFParagraph parrafo, String valor, int tamano, boolean negrita, String color) {
        XWPFRun run = parrafo.createRun();
        run.setFontFamily(FUENTE);
        run.setFontSize(tamano);
        run.setBold(negrita);
        run.setColor(color);
        run.setText(valor);
        return run;
    }

    /**
     * Pega una imagen a su tamaño en centímetros. El texto alternativo es el
     * nombre del fichero que Word enseña si la imagen no se puede pintar.
     */
    private static void imagen(XWPFParagraph parrafo, byte[] datos, PictureType tipo, String nombre,
                               double anchoCm, double altoCm) throws IOException {
        try {
            parrafo.createRun().addPicture(new ByteArrayInputStream(datos), tipo, nombre,
                    (int) Math.round(anchoCm * EMU_POR_CM), (int) Math.round(altoCm * EMU_POR_CM));
        } catch (InvalidFormatException e) {
            throw new IOException("no se ha podido meter la imagen " + nombre + " en el Word", e);
        }
    }

    /** Borde de una celda por los cuatro lados. */
    private static void bordes(XWPFTableCell celda, int grosor, String color) {
        for (boolean arriba : new boolean[] {true, false}) {
            borde(celda, arriba, grosor, color);
        }
        CTTcBorders bordes = bordesDe(celda);
        for (CTBorder lado : new CTBorder[] {bordes.addNewLeft(), bordes.addNewRight()}) {
            ajustar(lado, grosor, color);
        }
    }

    /** Filete de una celda, arriba o abajo. */
    private static void borde(XWPFTableCell celda, boolean arriba, int grosor, String color) {
        CTTcBorders bordes = bordesDe(celda);
        ajustar(arriba ? bordes.addNewTop() : bordes.addNewBottom(), grosor, color);
    }

    private static CTTcBorders bordesDe(XWPFTableCell celda) {
        CTTcPr propiedades = celda.getCTTc().isSetTcPr() ? celda.getCTTc().getTcPr() : celda.getCTTc().addNewTcPr();
        return propiedades.isSetTcBorders() ? propiedades.getTcBorders() : propiedades.addNewTcBorders();
    }

    private static void ajustar(CTBorder borde, int grosor, String color) {
        borde.setVal(STBorder.SINGLE);
        borde.setSz(BigInteger.valueOf(grosor));
        borde.setSpace(BigInteger.ZERO);
        borde.setColor(color);
    }
}

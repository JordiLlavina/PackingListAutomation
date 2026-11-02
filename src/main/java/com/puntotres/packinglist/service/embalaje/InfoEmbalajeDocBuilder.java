package com.puntotres.packinglist.service.embalaje;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
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
 * familia (Gentium Basic, incrustada en el fichero: ver
 * {@link FuenteIncrustada}), todo en negros y grises, sin ningún color, y
 * <b>todo el texto en mayúsculas</b> (se pasa en un solo sitio, el de los
 * runs: {@code texto}).
 * Arriba, la cabecera en una fila de tres columnas: la foto, el artículo
 * (cliente · temporada · taller, referencia, color, nombre del bolso y las
 * fichas del envío) y el QR, sin marco. Debajo, a todo el ancho, el
 * embalaje; luego las destinaciones, el reparto y los comentarios. Ningún
 * bloque lleva título de sección: se separan con aire, sin filetes
 * (decisión del usuario sobre la hoja impresa).
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

    private static final String FUENTE = FuenteIncrustada.NOMBRE;

    /** Paleta: solo negros y grises. Nada de color, por decisión del usuario. */
    private static final String NEGRO = "000000";
    /** Texto secundario: la línea de cliente · temporada · taller, la del color, el nombre del bolso. */
    private static final String GRIS_OSCURO = "404040";
    /** Rótulos pequeños: los de las fichas, las filas del embalaje y la cabecera de la tabla. */
    private static final String GRIS_ROTULO = "808080";
    /** Rellenos: las fichas, la cabecera de la tabla y el recuadro de comentarios. */
    private static final String GRIS_FONDO = "F2F2F2";
    /** Filetes y bordes de recuadro. */
    private static final String GRIS_FILETE = "BFBFBF";
    private static final String BLANCO = "FFFFFF";

    // Tamaños de letra, en puntos.
    /** Cliente · temporada · taller: es contexto, no lo que se busca con la vista; la referencia manda. */
    private static final int TAMANO_TITULO = 12;
    private static final int TAMANO_TITULO_QR = 9;
    private static final int TAMANO_ROTULO_FICHA = 8;
    private static final int TAMANO_VALOR_FICHA = 20;
    private static final int TAMANO_REFERENCIA = 26;
    private static final int TAMANO_COLOR = 14;
    private static final int TAMANO_NOMBRE = 12;
    /** La lista del embalaje, rótulos y valores (también la instrucción especial). */
    private static final int TAMANO_EMBALAJE = 12;
    /** La tabla de destinaciones, cabecera y filas; solo la fila Total es mayor. */
    private static final int TAMANO_TABLA = 12;
    private static final int TAMANO_TOTAL_TABLA = 14;
    /** El reparto en cajas: el taller lo lee caja a caja, así que va al tamaño de la fila Total. */
    private static final int TAMANO_REPARTO = 14;
    private static final int TAMANO_COMENTARIOS = 14;

    // Filetes en octavos de punto.
    private static final int FILETE_FINO = 4;
    private static final int FILETE_TOTAL = 12;
    private static final int FILETE_COMENTARIOS = 8;

    /** Medidas de las imágenes. Un centímetro son 360.000 EMU, la unidad de Word. */
    private static final int EMU_POR_CM = 360_000;
    private static final int TWIPS_POR_CM = 567;
    private static final double ANCHO_QR_CM = 3.5;
    /**
     * Tope de alto de la foto: con la foto en la cabecera, una vertical de
     * móvil a todo el ancho de su columna mediría más de 5 cm y estiraría la
     * fila entera, que es la que más papel ocupa.
     */
    private static final double ALTO_MAXIMO_FOTO_CM = 5;
    /**
     * Píxeles mínimos del lado del QR: 3,5 cm a unos 300 ppp. {@link CodigoQr}
     * lo redondea hacia arriba a un número entero de píxeles por módulo.
     */
    private static final int PIXELES_QR = 413;

    /** Aire entre bloques, en puntos: es lo único que los separa, no hay filetes ni títulos. */
    private static final int ESPACIO_BLOQUES_PT = 24;
    /** Aire de la cabecera, en veinteavos: entre la línea del cliente y la referencia, y entre el artículo y las fichas. */
    private static final int ESPACIO_TRAS_TITULO = 80;
    private static final int ESPACIO_ANTES_NOMBRE = 40;
    private static final int ESPACIO_ANTES_FICHAS = 200;
    /** Alto de línea del reparto: la letra de 14 pt con dos puntos de aire, menos que el automático. */
    private static final double ALTO_LINEA_REPARTO_PT = 16;
    private static final int MARGEN_CELDA_FICHA = 50;
    private static final int MARGEN_LATERAL_FICHA = 160;
    private static final int MARGEN_CELDA_FILA = 10;
    private static final int MARGEN_INFERIOR_FILA = 60;
    /**
     * Aire de arriba y abajo de cada fila de destinación, en veinteavos: 40
     * más por lado que la primera versión, que el usuario quería algo más alta.
     */
    private static final int MARGEN_VERTICAL_TABLA = 50;
    private static final int MARGEN_LATERAL_TABLA = 100;
    private static final int MARGEN_CELDA_COMENTARIOS = 70;
    private static final int MARGEN_LATERAL_COMENTARIOS = 180;
    /** Aire entre el título del QR y el código, en veinteavos. */
    private static final int AIRE_TRAS_TITULO_QR = 30;
    /**
     * Cada ficha del envío mide lo que su contenido pide (3,5 cm), a la
     * izquierda y con el resto de la fila vacío: ancho fijo, para que no se
     * estiren con o sin QR. El hueco blanco entre las dos (0,25 cm, 57 octavos
     * de punto) es un filete blanco que come algo de cada una.
     */
    private static final int ANCHO_FICHA = (int) Math.round(3.5 * TWIPS_POR_CM);
    private static final int HUECO_ENTRE_FICHAS = 57;

    /**
     * Columnas de la cabecera, en veinteavos: la foto (4,5 cm), el QR (4,5 cm: el título en francés cabe en una línea)
     * y el artículo, que se queda con el resto. Sin enlace no hay columna del
     * QR y el artículo se queda también con su ancho; sin foto la columna se
     * conserva en blanco.
     */
    private static final int ANCHO_COLUMNA_FOTO = (int) Math.round(4.5 * TWIPS_POR_CM);
    private static final int ANCHO_COLUMNA_QR = (int) Math.round(4.5 * TWIPS_POR_CM);
    /** Separación entre la foto y el artículo: la foto nunca llega al borde derecho de su columna. */
    private static final int HUECO_FOTO = 340;
    /** Ancho de los rótulos del embalaje: el mayor, «ETIQUETA CARTÓN INTERIOR», cabe en una línea a 12 pt. */
    private static final int ANCHO_ROTULO_FILA = 3500;
    /**
     * Parte del ancho útil que ocupa la tabla de destinaciones, pegada a la
     * izquierda (decisión del usuario): a todo el ancho, entre el destino y su
     * cantidad quedaba un hueco que la vista tenía que recorrer entero.
     */
    static final double FRACCION_ANCHO_DESTINACIONES = 0.70;
    /** Proporciones de las columnas Destinación / Nº pedido / Cantidad: las de cuando iba a todo el ancho. */
    private static final int[] PROPORCIONES_DESTINACIONES = {4400, 3400, 2506};
    private static final int[] ANCHOS_DESTINACIONES = repartir(
            (int) Math.round(ANCHO_UTIL * FRACCION_ANCHO_DESTINACIONES), PROPORCIONES_DESTINACIONES);

    private static final String[] CABECERAS_DESTINACIONES = {"Destinación", "Nº pedido", "Cantidad"};
    private static final String TITULO_QR = "Guide d'emballage du sac";
    /** Los textos se pasan a mayúsculas con las reglas del castellano; los acentos se conservan. */
    private static final Locale CASTELLANO = Locale.forLanguageTag("es");
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
            escribirCabecera(doc, datos, fotoJpeg);
            espacio(doc, ESPACIO_BLOQUES_PT);
            escribirEmbalaje(doc, datos);
            espacio(doc, ESPACIO_BLOQUES_PT);
            escribirDestinaciones(doc, datos);
            escribirReparto(doc, datos);
            escribirComentarios(doc, datos);
            // Una tabla al final del cuerpo la deja Word sin párrafo detrás y
            // abre el documento "reparado" en algunas versiones: este lo cierra.
            espacio(doc, 1);
            FuenteIncrustada.aplicar(doc);
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
     * La cabecera, una fila de tres columnas a la misma altura (decisión del
     * usuario): la foto arriba a la izquierda, que es lo primero de la hoja;
     * el artículo en medio; y, solo si hay enlace, el QR a la derecha. Sin
     * enlace no hay tercera columna y el artículo se queda con su ancho.
     */
    private void escribirCabecera(XWPFDocument doc, DatosHojaEmbalaje datos, byte[] fotoJpeg) throws IOException {
        boolean conQr = datos.tieneEnlace();
        int anchoArticulo = ANCHO_UTIL - ANCHO_COLUMNA_FOTO - (conQr ? ANCHO_COLUMNA_QR : 0);
        XWPFTable tabla = conQr
                ? nuevaTabla(doc, 1, ANCHO_COLUMNA_FOTO, anchoArticulo, ANCHO_COLUMNA_QR)
                : nuevaTabla(doc, 1, ANCHO_COLUMNA_FOTO, anchoArticulo);
        XWPFTableRow fila = tabla.getRow(0);
        escribirFoto(fila.getCell(0), fotoJpeg);
        escribirArticulo(fila.getCell(1), datos);
        if (conQr) {
            escribirQr(fila.getCell(2), datos);
        }
    }

    /**
     * La foto, a todo el ancho de su columna menos el hueco con el artículo,
     * conservando su proporción: se lee la medida del fichero en vez de forzar
     * un alto, o un bolso saldría achatado. Si así pasaría de 5 cm de alto
     * (una foto vertical de móvil) se reduce entera. Sin foto, o con una que
     * no se deja leer, la celda conserva su ancho y se queda en blanco.
     */
    private static void escribirFoto(XWPFTableCell celda, byte[] fotoJpeg) throws IOException {
        XWPFParagraph parrafo = parrafo(celda, ParagraphAlignment.LEFT);
        double[] medida = medidaFoto(fotoJpeg);
        if (medida == null) {
            return;
        }
        double ancho = (ANCHO_COLUMNA_FOTO - HUECO_FOTO) / (double) TWIPS_POR_CM;
        double alto = ancho * medida[1] / medida[0];
        if (alto > ALTO_MAXIMO_FOTO_CM) {
            alto = ALTO_MAXIMO_FOTO_CM;
            ancho = alto * medida[0] / medida[1];
        }
        imagen(parrafo, fotoJpeg, PictureType.JPEG, "foto.jpg", ancho, alto);
    }

    /**
     * El artículo, de arriba abajo: cliente · temporada · taller (12 pt, gris,
     * sin negrita: es contexto), la referencia en grande, el color, el nombre
     * del bolso y las dos fichas del envío.
     */
    private void escribirArticulo(XWPFTableCell celda, DatosHojaEmbalaje datos) {
        texto(parrafo(celda, ParagraphAlignment.LEFT), titulo(datos), TAMANO_TITULO, false, GRIS_OSCURO);

        XWPFParagraph referencia = parrafo(celda, ParagraphAlignment.LEFT);
        referencia.setSpacingBefore(ESPACIO_TRAS_TITULO);
        texto(referencia, datos.referencia(), TAMANO_REFERENCIA, true, NEGRO);
        String color = (datos.codigoColor() + " " + datos.descripcionColor()).trim();
        if (!color.isEmpty()) {
            texto(parrafo(celda, ParagraphAlignment.LEFT), color, TAMANO_COLOR, false, GRIS_OSCURO);
        }
        if (!datos.nombreBolso().isBlank()) {
            XWPFParagraph nombre = parrafo(celda, ParagraphAlignment.LEFT);
            nombre.setSpacingBefore(ESPACIO_ANTES_NOMBRE);
            texto(nombre, datos.nombreBolso(), TAMANO_NOMBRE, false, GRIS_OSCURO);
        }
        espacioEnCelda(celda, ESPACIO_ANTES_FICHAS);
        escribirFichasDeEnvio(celda, datos);
    }

    /**
     * Dos fichas en fila, Nº envío y nota, de ancho fijo y con un hueco blanco
     * entre ellas. La fecha no se imprime (decisión del usuario): sigue
     * guardada en la hoja, pero en el papel no le dice nada al taller.
     */
    private void escribirFichasDeEnvio(XWPFTableCell celda, DatosHojaEmbalaje datos) {
        XWPFTable tabla = tablaEnCelda(celda, 1, ANCHO_FICHA, ANCHO_FICHA);
        tabla.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, HUECO_ENTRE_FICHAS, 0, BLANCO);
        tabla.setCellMargins(MARGEN_CELDA_FICHA, MARGEN_LATERAL_FICHA, MARGEN_CELDA_FICHA, MARGEN_LATERAL_FICHA);
        String[] rotulos = {"Nº envío", "Nota"};
        String[] valores = {String.valueOf(datos.numeroEnvio()), datos.nota()};
        for (int i = 0; i < rotulos.length; i++) {
            XWPFTableCell ficha = tabla.getRow(0).getCell(i);
            ficha.setColor(GRIS_FONDO);
            texto(parrafo(ficha, ParagraphAlignment.LEFT), rotulos[i], TAMANO_ROTULO_FICHA, false, GRIS_ROTULO);
            texto(parrafo(ficha, ParagraphAlignment.LEFT), valores[i], TAMANO_VALOR_FICHA, true, NEGRO);
        }
    }

    /**
     * El QR de la guía con su título en francés encima, sin marco (decisión
     * del usuario), centrados en su columna.
     */
    private static void escribirQr(XWPFTableCell celda, DatosHojaEmbalaje datos) throws IOException {
        XWPFParagraph titulo = parrafo(celda, ParagraphAlignment.CENTER);
        titulo.setSpacingAfter(AIRE_TRAS_TITULO_QR);
        texto(titulo, TITULO_QR, TAMANO_TITULO_QR, true, NEGRO);
        XWPFParagraph qr = parrafo(celda, ParagraphAlignment.CENTER);
        imagen(qr, CodigoQr.png(datos.enlaceVideo().trim(), PIXELES_QR), PictureType.PNG,
                "qr.png", ANCHO_QR_CM, ANCHO_QR_CM);
    }

    /**
     * El embalaje, a todo el ancho y todo a 12 pt: caja, unidades por caja,
     * bolsa, etiqueta interior y, si la hay, la instrucción especial. El total
     * de unidades no se repite aquí: ya lo dice la fila Total de las
     * destinaciones.
     */
    private void escribirEmbalaje(XWPFDocument doc, DatosHojaEmbalaje datos) {
        List<String[]> filas = new ArrayList<>();
        filas.add(new String[] {"Caja", datos.medidaCaja()});
        filas.add(new String[] {"Unidades por caja",
                datos.unidadesPorCaja() == null ? "" : String.valueOf(datos.unidadesPorCaja())});
        filas.add(new String[] {"Bolsa de tela", datos.bolsaTela()});
        filas.add(new String[] {"Etiqueta cartón interior", fraseEtiquetaInterior(datos.etiquetaInterior())});
        if (!datos.instruccionEspecial().isBlank()) {
            filas.add(new String[] {"Instrucción especial", datos.instruccionEspecial()});
        }

        XWPFTable tabla = nuevaTabla(doc, filas.size(), ANCHO_ROTULO_FILA, ANCHO_UTIL - ANCHO_ROTULO_FILA);
        tabla.setCellMargins(MARGEN_CELDA_FILA, 0, MARGEN_CELDA_FILA, MARGEN_INFERIOR_FILA);
        for (int i = 0; i < filas.size(); i++) {
            XWPFTableRow fila = tabla.getRow(i);
            texto(parrafo(fila.getCell(0), ParagraphAlignment.LEFT), filas.get(i)[0],
                    TAMANO_EMBALAJE, false, GRIS_ROTULO);
            texto(parrafo(fila.getCell(1), ParagraphAlignment.LEFT), filas.get(i)[1],
                    TAMANO_EMBALAJE, true, NEGRO);
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
        tabla.setTableAlignment(TableRowAlign.LEFT);
        tabla.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, FILETE_FINO, 0, GRIS_FILETE);
        tabla.setCellMargins(MARGEN_VERTICAL_TABLA, MARGEN_LATERAL_TABLA, MARGEN_VERTICAL_TABLA, MARGEN_LATERAL_TABLA);

        for (int i = 0; i < CABECERAS_DESTINACIONES.length; i++) {
            XWPFTableCell celda = tabla.getRow(0).getCell(i);
            celda.setColor(GRIS_FONDO);
            texto(parrafo(celda, alineacionDeColumna(i)), CABECERAS_DESTINACIONES[i],
                    TAMANO_TABLA, true, GRIS_ROTULO);
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

    /**
     * Reparte {@code total} entre las columnas según sus proporciones; la
     * última se lleva el redondeo, para que la suma sea exactamente el total.
     */
    private static int[] repartir(int total, int... proporciones) {
        int suma = 0;
        for (int proporcion : proporciones) {
            suma += proporcion;
        }
        int[] anchos = new int[proporciones.length];
        int usado = 0;
        for (int i = 0; i < proporciones.length - 1; i++) {
            anchos[i] = (int) Math.round(total * (double) proporciones[i] / suma);
            usado += anchos[i];
        }
        anchos[proporciones.length - 1] = total - usado;
        return anchos;
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
     * Un recuadro gris con el texto en negrita, sin título (el usuario quitó
     * «COMENTARIOS»: el recuadro ya dice que es aparte). Sin texto no sale.
     */
    private void escribirComentarios(XWPFDocument doc, DatosHojaEmbalaje datos) {
        if (datos.comentarios().isBlank()) {
            return;
        }
        espacio(doc, ESPACIO_BLOQUES_PT);
        XWPFTable tabla = nuevaTabla(doc, 1, ANCHO_UTIL);
        tabla.setCellMargins(MARGEN_CELDA_COMENTARIOS, MARGEN_LATERAL_COMENTARIOS,
                MARGEN_CELDA_COMENTARIOS, MARGEN_LATERAL_COMENTARIOS);
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
     * él abre el documento como dañado. Si la celda solo lleva el párrafo
     * vacío que POI pone al crearla, se quita: delante de la tabla sería una
     * línea en blanco (el marco del QR quedaría más bajo que la foto).
     */
    private static XWPFTable tablaEnCelda(XWPFTableCell celda, int filas, int... anchos) {
        if (celda.getBodyElements().size() == 1 && celda.getParagraphs().size() == 1
                && celda.getParagraphs().get(0).getRuns().isEmpty()) {
            celda.removeParagraph(0);
        }
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

    /**
     * El único sitio donde se escribe texto: aquí se pasa todo a mayúsculas
     * (decisión del usuario), para que ningún bloque pueda quedarse sin pasar.
     */
    private static XWPFRun texto(XWPFParagraph parrafo, String valor, int tamano, boolean negrita, String color) {
        XWPFRun run = parrafo.createRun();
        run.setFontFamily(FUENTE);
        run.setFontSize(tamano);
        run.setBold(negrita);
        run.setColor(color);
        run.setText(valor.toUpperCase(CASTELLANO));
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

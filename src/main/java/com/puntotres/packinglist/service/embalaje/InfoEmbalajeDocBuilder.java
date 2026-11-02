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
 * letra: bloques separados, rótulos pequeños en gris y valores grandes en
 * negrita. El naranja de la marca solo se usa para lo que el taller no puede
 * pasar por alto (el filete de cada sección y la instrucción especial).
 *
 * Las secciones que no tienen nada que decir ni se pintan: ni el QR sin
 * enlace, ni «Reparto en cajas» o «Comentarios» sin texto, ni la fila de la
 * instrucción especial. Un título con la tabla vacía debajo parecería un dato
 * que falta. Si no cabe en un A4, Word sigue en la hoja siguiente; no se
 * recorta nada. Sin plantilla {@code .docx}: la maquetación son las
 * constantes de esta clase.
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

    /** Paleta: la tinta del texto, los grises de apoyo y los colores de aviso de la aplicación. */
    private static final String TINTA = "1C2733";
    private static final String GRIS_TEXTO = "6B7785";
    private static final String GRIS_FONDO = "F2F4F6";
    private static final String GRIS_FILETE = "D4DAE1";
    private static final String NARANJA = "FF8900";
    private static final String AVISO_FONDO = "FFF7E0";
    private static final String AVISO_BORDE = "E6C34A";
    private static final String BLANCO = "FFFFFF";

    // Tamaños de letra, en puntos.
    private static final int TAMANO_TITULO = 22;
    private static final int TAMANO_SUBTITULO = 11;
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
    private static final int TAMANO_CABECERA_TABLA = 9;
    private static final int TAMANO_REPARTO = 11;
    private static final int TAMANO_COMENTARIOS = 12;

    // Filetes en octavos de punto.
    private static final int FILETE_FINO = 4;
    private static final int FILETE_SECCION = 12;
    private static final int FILETE_QR = 12;

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

    /** Aire entre bloques en puntos, y alto de la fila de datos de las tablas en veinteavos. */
    private static final int ESPACIO_BLOQUES_PT = 6;
    private static final int ESPACIO_SECCION_ANTES = 140;
    private static final int ESPACIO_SECCION_DESPUES = 50;
    /** Alto de línea del reparto: la letra de 11 pt con un punto y medio de aire, menos que el automático. */
    private static final double ALTO_LINEA_REPARTO_PT = 12.5;
    private static final int MARGEN_CELDA_FICHA = 50;
    private static final int MARGEN_CELDA_FILA = 10;
    private static final int MARGEN_CELDA_TABLA = 10;
    private static final int MARGEN_CELDA_COMENTARIOS = 70;
    /** Separación entre la foto y el texto del artículo. */
    private static final int HUECO_FOTO = 340;
    private static final int ANCHO_COLUMNA_QR = 2900;
    private static final int ANCHO_ROTULO_FILA = 3400;
    private static final int[] ANCHOS_DESTINACIONES = {4400, 3400, 2506};

    private static final String[] CABECERAS_DESTINACIONES = {"Destinación", "Nº pedido", "Cantidad"};

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
     * El Word de la hoja. {@code fotoJpeg} es la foto principal del modelo en
     * el catálogo, o {@code null} si no tiene: la celda se queda vacía.
     */
    public byte[] generar(DatosHojaEmbalaje datos, byte[] fotoJpeg) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            configurarPagina(doc);
            escribirCabecera(doc, datos);
            espacio(doc, ESPACIO_BLOQUES_PT);
            escribirFichasDeEnvio(doc, datos);
            espacio(doc, ESPACIO_BLOQUES_PT);
            escribirArticulo(doc, datos, fotoJpeg);
            escribirEmbalaje(doc, datos);
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
     * Banda de cabecera: el título y, en gris, cliente · temporada · taller; a
     * la derecha, solo si hay enlace, el recuadro con el QR de la guía.
     */
    private void escribirCabecera(XWPFDocument doc, DatosHojaEmbalaje datos) throws IOException {
        XWPFTable tabla = nuevaTabla(doc, 1, ANCHO_UTIL - ANCHO_COLUMNA_QR, ANCHO_COLUMNA_QR);
        XWPFTableCell izquierda = tabla.getRow(0).getCell(0);
        texto(parrafo(izquierda, ParagraphAlignment.LEFT), "INSTRUCCIONES DE EMBALAJE",
                TAMANO_TITULO, true, TINTA);
        List<String> contexto = new ArrayList<>();
        for (String dato : new String[] {nombreDelCliente(datos), datos.temporada(), datos.taller()}) {
            if (!dato.isBlank()) {
                contexto.add(dato);
            }
        }
        XWPFParagraph gris = parrafo(izquierda, ParagraphAlignment.LEFT);
        gris.setSpacingBefore(40);
        texto(gris, String.join("  ·  ", contexto), TAMANO_SUBTITULO, false, GRIS_TEXTO);

        if (datos.tieneEnlace()) {
            XWPFTableCell recuadro = tabla.getRow(0).getCell(1);
            bordes(recuadro, FILETE_QR, TINTA);
            XWPFParagraph titulo = parrafo(recuadro, ParagraphAlignment.CENTER);
            titulo.setSpacingBefore(40);
            titulo.setSpacingAfter(30);
            XWPFRun run = texto(titulo, "Guide d'emballage du sac", TAMANO_TITULO_QR, true, TINTA);
            run.setSmallCaps(true);
            XWPFParagraph qr = parrafo(recuadro, ParagraphAlignment.CENTER);
            imagen(qr, CodigoQr.png(datos.enlaceVideo().trim(), PIXELES_QR), PictureType.PNG,
                    "qr.png", ANCHO_QR_CM, ANCHO_QR_CM);
        }
    }

    /** Tres fichas en fila, Nº envío, nota del ERP y fecha, con un hueco blanco entre ellas. */
    private void escribirFichasDeEnvio(XWPFDocument doc, DatosHojaEmbalaje datos) {
        int ancho = ANCHO_UTIL / 3;
        XWPFTable tabla = nuevaTabla(doc, 1, ancho, ancho, ANCHO_UTIL - 2 * ancho);
        tabla.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 48, 0, BLANCO);
        tabla.setCellMargins(MARGEN_CELDA_FICHA, 160, MARGEN_CELDA_FICHA, 160);
        String[] rotulos = {"Nº ENVÍO", "NOTA ICSUITE", "FECHA"};
        String[] valores = {String.valueOf(datos.numeroEnvio()), datos.nota(),
                datos.fecha() == null ? "" : datos.fecha().format(FORMATO_FECHA)};
        for (int i = 0; i < rotulos.length; i++) {
            XWPFTableCell celda = tabla.getRow(0).getCell(i);
            celda.setColor(GRIS_FONDO);
            texto(parrafo(celda, ParagraphAlignment.LEFT), rotulos[i], TAMANO_ROTULO_FICHA, false, GRIS_TEXTO);
            texto(parrafo(celda, ParagraphAlignment.LEFT), valores[i], TAMANO_VALOR_FICHA, true, TINTA);
        }
    }

    /**
     * La foto a la izquierda y, a la derecha, lo que identifica el artículo.
     * La foto va a 5 cm de ancho conservando su proporción: se lee la medida
     * del fichero en vez de forzar un alto, o un bolso saldría achatado. Si es
     * vertical y pasaría de 4 cm de alto se reduce entera: una foto de móvil
     * en vertical a 5 cm de ancho mide casi 7 de alto y empuja los comentarios
     * a una segunda hoja, que es justo lo que esta hoja no puede hacer.
     */
    private void escribirArticulo(XWPFDocument doc, DatosHojaEmbalaje datos, byte[] fotoJpeg)
            throws IOException {
        int anchoFoto = (int) Math.round(ANCHO_FOTO_CM * TWIPS_POR_CM) + HUECO_FOTO;
        XWPFTable tabla = nuevaTabla(doc, 1, anchoFoto, ANCHO_UTIL - anchoFoto);
        tabla.setCellMargins(0, 0, 0, 0);
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

        XWPFTableCell texto = tabla.getRow(0).getCell(1);
        texto(parrafo(texto, ParagraphAlignment.LEFT), datos.referencia(), TAMANO_REFERENCIA, true, TINTA);
        String color = (datos.codigoColor() + " " + datos.descripcionColor()).trim();
        if (!color.isEmpty()) {
            texto(parrafo(texto, ParagraphAlignment.LEFT), color, TAMANO_COLOR, false, TINTA);
        }
        if (!datos.nombreBolso().isBlank()) {
            XWPFParagraph nombre = parrafo(texto, ParagraphAlignment.LEFT);
            nombre.setSpacingBefore(40);
            texto(nombre, datos.nombreBolso().toUpperCase(), TAMANO_NOMBRE, false, GRIS_TEXTO);
        }
        XWPFParagraph total = parrafo(texto, ParagraphAlignment.LEFT);
        total.setSpacingBefore(240);
        texto(total, "TOTAL " + datos.totalUnidades() + " UNIDADES", TAMANO_TOTAL, true, TINTA);
    }

    /** Rótulo y valor de la caja, la bolsa, la etiqueta interior y, destacada, la instrucción especial. */
    private void escribirEmbalaje(XWPFDocument doc, DatosHojaEmbalaje datos) {
        titulo(doc, "EMBALAJE");
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
        tabla.setCellMargins(MARGEN_CELDA_FILA, 0, MARGEN_CELDA_FILA, 60);
        for (int i = 0; i < filas.size(); i++) {
            boolean instruccion = filas.get(i)[0].startsWith("Instrucción");
            XWPFTableRow fila = tabla.getRow(i);
            texto(parrafo(fila.getCell(0), ParagraphAlignment.LEFT), filas.get(i)[0],
                    TAMANO_ROTULO_FILA, false, GRIS_TEXTO);
            texto(parrafo(fila.getCell(1), ParagraphAlignment.LEFT), filas.get(i)[1],
                    instruccion ? TAMANO_INSTRUCCION : TAMANO_VALOR_FILA, true,
                    instruccion ? NARANJA : TINTA);
        }
    }

    /** Cabecera gris, un filete fino entre filas, la cantidad a la derecha y el total en negrita. */
    private void escribirDestinaciones(XWPFDocument doc, DatosHojaEmbalaje datos) {
        titulo(doc, "DESTINACIONES");
        List<DestinacionHoja> destinaciones = datos.destinaciones();
        XWPFTable tabla = nuevaTabla(doc, destinaciones.size() + 2, ANCHOS_DESTINACIONES);
        tabla.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, FILETE_FINO, 0, GRIS_FILETE);
        tabla.setCellMargins(MARGEN_CELDA_TABLA, 100, MARGEN_CELDA_TABLA, 100);

        for (int i = 0; i < CABECERAS_DESTINACIONES.length; i++) {
            XWPFTableCell celda = tabla.getRow(0).getCell(i);
            celda.setColor(GRIS_FONDO);
            texto(parrafo(celda, alineacionDeColumna(i)), CABECERAS_DESTINACIONES[i],
                    TAMANO_CABECERA_TABLA, true, GRIS_TEXTO);
        }
        for (int i = 0; i < destinaciones.size(); i++) {
            DestinacionHoja destinacion = destinaciones.get(i);
            filaDestinacion(tabla.getRow(i + 1), false, destinacion.destino(), destinacion.pedido(),
                    String.valueOf(destinacion.cantidad()));
        }
        XWPFTableRow total = tabla.getRow(destinaciones.size() + 1);
        filaDestinacion(total, true, "Total", "", String.valueOf(datos.totalUnidades()));
        for (XWPFTableCell celda : total.getTableCells()) {
            borde(celda, true, FILETE_SECCION, TINTA);
        }
    }

    private void filaDestinacion(XWPFTableRow fila, boolean negrita, String... valores) {
        for (int i = 0; i < valores.length; i++) {
            texto(parrafo(fila.getCell(i), alineacionDeColumna(i)), valores[i], TAMANO_TABLA, negrita, TINTA);
        }
    }

    /** La cantidad (última columna) a la derecha para que las cifras se sumen a ojo. */
    private static ParagraphAlignment alineacionDeColumna(int columna) {
        return columna == CABECERAS_DESTINACIONES.length - 1 ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT;
    }

    /** El texto libre del reparto, una línea por párrafo. Sin texto no sale ni el título. */
    private void escribirReparto(XWPFDocument doc, DatosHojaEmbalaje datos) {
        if (datos.repartoCajas().isBlank()) {
            return;
        }
        titulo(doc, "REPARTO EN CAJAS");
        for (String linea : lineas(datos.repartoCajas())) {
            XWPFParagraph parrafo = parrafo(doc.createParagraph(), ParagraphAlignment.LEFT);
            altoExacto(parrafo, ALTO_LINEA_REPARTO_PT);
            texto(parrafo, linea, TAMANO_REPARTO, false, TINTA);
        }
    }

    /** Recuadro con los colores de aviso de la aplicación. Sin texto no sale ni el título. */
    private void escribirComentarios(XWPFDocument doc, DatosHojaEmbalaje datos) {
        if (datos.comentarios().isBlank()) {
            return;
        }
        titulo(doc, "COMENTARIOS");
        XWPFTable tabla = nuevaTabla(doc, 1, ANCHO_UTIL);
        tabla.setCellMargins(MARGEN_CELDA_COMENTARIOS, 180, MARGEN_CELDA_COMENTARIOS, 180);
        XWPFTableCell celda = tabla.getRow(0).getCell(0);
        celda.setColor(AVISO_FONDO);
        bordes(celda, 8, AVISO_BORDE);
        for (String linea : lineas(datos.comentarios())) {
            texto(parrafo(celda, ParagraphAlignment.LEFT), linea, TAMANO_COMENTARIOS, true, TINTA);
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

    /** El título de una sección: negrita, mayúsculas y un filete naranja debajo, el color de la marca. */
    private static void titulo(XWPFDocument doc, String rotulo) {
        XWPFParagraph titulo = doc.createParagraph();
        titulo.setSpacingBefore(ESPACIO_SECCION_ANTES);
        titulo.setSpacingAfter(ESPACIO_SECCION_DESPUES);
        titulo.setKeepNext(true);
        CTPPr propiedades = titulo.getCTP().isSetPPr() ? titulo.getCTP().getPPr() : titulo.getCTP().addNewPPr();
        CTBorder filete = propiedades.addNewPBdr().addNewBottom();
        filete.setVal(STBorder.SINGLE);
        filete.setSz(BigInteger.valueOf(FILETE_SECCION));
        filete.setSpace(BigInteger.valueOf(2));
        filete.setColor(NARANJA);
        texto(titulo, rotulo, TAMANO_SECCION, true, TINTA);
    }

    /** Un párrafo vacío de alto exacto: el aire entre dos tablas, que sin él Word fusiona. */
    private static void espacio(XWPFDocument doc, int puntos) {
        altoExacto(doc.createParagraph(), puntos);
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

    /**
     * Tabla a todo el ancho, de medidas fijas (la del grid y la de cada
     * celda) y sin ningún borde: cada bloque se dibuja luego los suyos.
     * Con anchos "automáticos" Word reparte las columnas por el contenido y
     * el recuadro del QR o la columna de la foto se moverían de una hoja a otra.
     */
    private static XWPFTable nuevaTabla(XWPFDocument doc, int filas, int... anchos) {
        XWPFTable tabla = doc.createTable(filas, anchos.length);
        int total = 0;
        for (int ancho : anchos) {
            total += ancho;
        }
        CTTblPr propiedades = tabla.getCTTbl().getTblPr();
        CTTblWidth anchoTabla = propiedades.isSetTblW() ? propiedades.getTblW() : propiedades.addNewTblW();
        anchoTabla.setType(STTblWidth.DXA);
        anchoTabla.setW(BigInteger.valueOf(total));
        propiedades.addNewTblLayout().setType(STTblLayoutType.FIXED);

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
        tabla.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, TINTA);
        tabla.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, TINTA);
        tabla.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, TINTA);
        tabla.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, TINTA);
        tabla.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, TINTA);
        tabla.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, TINTA);
        tabla.setCellMargins(0, 0, 0, 0);
        return tabla;
    }

    /**
     * Un párrafo en la celda: el vacío que POI deja la primera vez y uno
     * nuevo cada vez después, sin espaciado (el aire lo ponen los márgenes
     * de celda y los espacios entre bloques).
     */
    private static XWPFParagraph parrafo(XWPFTableCell celda, ParagraphAlignment alineacion) {
        XWPFParagraph parrafo = celda.getParagraphs().size() == 1 && celda.getParagraphs().get(0).getRuns().isEmpty()
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

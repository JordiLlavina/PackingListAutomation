package com.puntotres.packinglist.service.etiquetasarticulo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.apache.poi.xwpf.usermodel.TableWidthType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;

/**
 * Escribe el Word de las etiquetas pequeñas de SMS: una rejilla de 5 columnas
 * por 13 filas por A4, una etiqueta por casilla, seguidas de izquierda a
 * derecha y de arriba abajo, tantas páginas como hagan falta.
 *
 * La maquetación está copiada de la plantilla del usuario
 * ("docs/Etiquetas Artículos/ETIQUETAS PEQUEÑAS Template.doc"), que es la de
 * las hojas de etiquetas adhesivas de 65 por A4 (38,1 x 21,2 mm): casillas de
 * 2160 x 1202 twips sin bordes ni hueco entre ellas, el texto de la primera
 * columna a 660 twips del borde izquierdo, 1 cm de margen arriba y abajo, y
 * Calibri a 7 pt. Ojo con ese 660: la plantilla es un .doc antiguo y allí el
 * sangrado de la tabla se mide hasta el TEXTO de la primera casilla, no hasta
 * su borde; aquí se mide hasta el borde, así que la tabla empieza 96 twips
 * (el margen de la casilla) antes. Comprobado exportando los dos con Word a
 * PDF y comparando dónde cae el texto. Las medidas son exactas porque tienen que caer encima de cada
 * adhesivo: una casilla que crece con su texto descuadra todas las de debajo.
 * Sin plantilla .docx: la maquetación son las constantes de esta clase.
 *
 * Una línea que no cabe a lo ancho de la casilla baja de letra, de medio en
 * medio punto, hasta que cabe (un bolso de dos pieles escribe las dos:
 * "BOMBARDIER BLK FIX ENC 12MM CM CAMEL + NOBILE"); nunca se corta. Si ni a
 * {@link #TAMANO_MINIMO} cabe, se queda en ese tamaño y quien genera el
 * documento lo avisa ({@link #cabe}).
 */
public class EtiquetasSmsDocBuilder {

    static final int ANCHO_PAGINA = 11906;
    static final int ALTO_PAGINA = 16838;
    static final int COLUMNAS = 5;
    static final int FILAS_POR_PAGINA = 13;
    static final int ETIQUETAS_POR_PAGINA = COLUMNAS * FILAS_POR_PAGINA;
    static final int ANCHO_CELDA = 2160;
    static final int ALTO_CELDA = 1202;
    /** Aire a cada lado del texto dentro de la casilla, el de la plantilla. */
    static final int MARGEN_CELDA = 96;
    /** Donde empieza el texto de la primera columna, medido en la plantilla. */
    static final int TEXTO_PRIMERA_COLUMNA = 660;
    static final int MARGEN_IZQUIERDO = TEXTO_PRIMERA_COLUMNA - MARGEN_CELDA;
    static final int MARGEN_DERECHO = ANCHO_PAGINA - MARGEN_IZQUIERDO - COLUMNAS * ANCHO_CELDA;
    static final int MARGEN_VERTICAL = 567;
    static final String FUENTE = "Calibri";
    static final double TAMANO = 7;
    static final double TAMANO_MINIMO = 4;
    /** Alto de cada línea, fijo: siete líneas de 8,5 pt caben en la casilla (1.190 de 1.202 twips). */
    static final int ALTO_LINEA = 170;

    /** Lo que se puede escribir a lo ancho de una casilla, en puntos. */
    private static final double ANCHO_UTIL_PT = (ANCHO_CELDA - 2 * MARGEN_CELDA) / 20.0;
    /**
     * Ancho de cada letra de Calibri, en cuerpos, un poco por lo alto para que
     * sobre y no falte.
     */
    private static final double ANCHO_MAYUSCULA = 0.60;
    private static final double ANCHO_MINUSCULA = 0.50;
    private static final double ANCHO_CIFRA = 0.52;
    private static final double ANCHO_ESPACIO = 0.23;
    private static final double ANCHO_SIGNO = 0.40;

    public byte[] generar(List<EtiquetaSms> etiquetas) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            configurarPagina(doc);
            int filas = Math.max(1, (etiquetas.size() + COLUMNAS - 1) / COLUMNAS);
            XWPFTable rejilla = rejilla(doc, filas);
            for (int fila = 0; fila < filas; fila++) {
                XWPFTableRow renglon = rejilla.getRow(fila);
                renglon.setHeight(ALTO_CELDA);
                renglon.setHeightRule(TableRowHeightRule.EXACT);
                renglon.setCantSplitRow(true);
                for (int columna = 0; columna < COLUMNAS; columna++) {
                    int indice = fila * COLUMNAS + columna;
                    escribir(renglon.getCell(columna),
                            indice < etiquetas.size() ? etiquetas.get(indice).lineas() : List.of());
                }
            }
            // El párrafo que Word exige detrás de una tabla, de 1 pt: con su alto
            // normal empujaría una página en blanco cuando la última está llena.
            parrafoDeAlto(doc.createParagraph(), 20).createRun().setFontSize(1);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            doc.write(salida);
            return salida.toByteArray();
        }
    }

    /** Si la línea cabe a lo ancho de la casilla, aunque sea con la letra más pequeña. */
    public static boolean cabe(String linea) {
        return anchoEnCuerpos(linea) * TAMANO_MINIMO <= ANCHO_UTIL_PT;
    }

    /** El tamaño mayor, de medio en medio punto, al que la línea cabe en la casilla. */
    static double tamanoPara(String linea) {
        double cuerpos = anchoEnCuerpos(linea);
        for (double tamano = TAMANO; tamano > TAMANO_MINIMO; tamano -= 0.5) {
            if (cuerpos * tamano <= ANCHO_UTIL_PT) {
                return tamano;
            }
        }
        return TAMANO_MINIMO;
    }

    private static double anchoEnCuerpos(String linea) {
        double cuerpos = 0;
        for (char letra : (linea == null ? "" : linea).toCharArray()) {
            cuerpos += Character.isWhitespace(letra) ? ANCHO_ESPACIO
                    : Character.isDigit(letra) ? ANCHO_CIFRA
                    : Character.isUpperCase(letra) ? ANCHO_MAYUSCULA
                    : Character.isLowerCase(letra) ? ANCHO_MINUSCULA : ANCHO_SIGNO;
        }
        return cuerpos;
    }

    private static void configurarPagina(XWPFDocument doc) {
        CTBody cuerpo = doc.getDocument().getBody();
        CTSectPr seccion = cuerpo.isSetSectPr() ? cuerpo.getSectPr() : cuerpo.addNewSectPr();
        CTPageSz tamano = seccion.isSetPgSz() ? seccion.getPgSz() : seccion.addNewPgSz();
        tamano.setW(BigInteger.valueOf(ANCHO_PAGINA));
        tamano.setH(BigInteger.valueOf(ALTO_PAGINA));
        CTPageMar margen = seccion.isSetPgMar() ? seccion.getPgMar() : seccion.addNewPgMar();
        margen.setTop(BigInteger.valueOf(MARGEN_VERTICAL));
        margen.setBottom(BigInteger.valueOf(MARGEN_VERTICAL));
        margen.setLeft(BigInteger.valueOf(MARGEN_IZQUIERDO));
        margen.setRight(BigInteger.valueOf(MARGEN_DERECHO));
        margen.setHeader(BigInteger.ZERO);
        margen.setFooter(BigInteger.ZERO);
    }

    /** La tabla sin bordes y de anchos fijos: Word no la ensancha según el texto. */
    private static XWPFTable rejilla(XWPFDocument doc, int filas) {
        XWPFTable tabla = doc.createTable(filas, COLUMNAS);
        tabla.removeBorders();
        tabla.setWidth(COLUMNAS * ANCHO_CELDA);
        tabla.setWidthType(TableWidthType.DXA);
        tabla.setCellMargins(0, MARGEN_CELDA, 0, MARGEN_CELDA);
        CTTblPr propiedades = tabla.getCTTbl().getTblPr();
        CTTblLayoutType disposicion = propiedades.isSetTblLayout()
                ? propiedades.getTblLayout() : propiedades.addNewTblLayout();
        disposicion.setType(STTblLayoutType.FIXED);
        CTTblGrid columnas = tabla.getCTTbl().getTblGrid() != null
                ? tabla.getCTTbl().getTblGrid() : tabla.getCTTbl().addNewTblGrid();
        while (columnas.sizeOfGridColArray() > 0) {
            columnas.removeGridCol(0);
        }
        for (int columna = 0; columna < COLUMNAS; columna++) {
            columnas.addNewGridCol().setW(BigInteger.valueOf(ANCHO_CELDA));
        }
        for (XWPFTableRow fila : tabla.getRows()) {
            for (int columna = 0; columna < COLUMNAS; columna++) {
                XWPFTableCell celda = fila.getCell(columna);
                celda.setWidth(String.valueOf(ANCHO_CELDA));
                celda.setWidthType(TableWidthType.DXA);
            }
        }
        return tabla;
    }

    /** Una línea por párrafo; una casilla sin etiqueta se queda con su párrafo vacío. */
    private static void escribir(XWPFTableCell celda, List<String> lineas) {
        for (int i = 0; i < lineas.size(); i++) {
            XWPFParagraph parrafo = i == 0 && !celda.getParagraphs().isEmpty()
                    ? celda.getParagraphs().get(0) : celda.addParagraph();
            XWPFRun run = parrafoDeAlto(parrafo, ALTO_LINEA).createRun();
            run.setFontFamily(FUENTE);
            run.setFontSize(tamanoPara(lineas.get(i)));
            run.setText(lineas.get(i));
        }
        if (lineas.isEmpty() && !celda.getParagraphs().isEmpty()) {
            parrafoDeAlto(celda.getParagraphs().get(0), ALTO_LINEA);
        }
    }

    private static XWPFParagraph parrafoDeAlto(XWPFParagraph parrafo, int twips) {
        CTPPr propiedades = parrafo.getCTP().isSetPPr()
                ? parrafo.getCTP().getPPr() : parrafo.getCTP().addNewPPr();
        CTSpacing espaciado = propiedades.isSetSpacing()
                ? propiedades.getSpacing() : propiedades.addNewSpacing();
        espaciado.setBefore(BigInteger.ZERO);
        espaciado.setAfter(BigInteger.ZERO);
        espaciado.setLine(BigInteger.valueOf(twips));
        espaciado.setLineRule(STLineSpacingRule.EXACT);
        return parrafo;
    }
}

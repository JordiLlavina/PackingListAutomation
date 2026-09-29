package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

/**
 * Escribe el Word de fotos de una referencia: todas las fotos de su modelo,
 * seis por A4 en una cuadrícula de 2 columnas por 3 filas que llena la hoja
 * con márgenes estrechos, sin pie de foto (el nombre del fichero no dice
 * nada). Tantas hojas como hagan falta.
 *
 * El título —el modelo y, si el pedido lo trae, el nombre del bolso— va en la
 * CABECERA de página y no en el cuerpo: así se repite en cada hoja, que se
 * separan al imprimir, y la cuadrícula tiene el cuerpo entero para ella. Va
 * en el estilo integrado Title de Word (Título en español) y a 22 pt; ni
 * temporada ni pieles, que el Word es del modelo y vale para todas. Sin plantilla .docx: la
 * maquetación son las constantes de esta clase.
 */
public class FotosCorteDocBuilder {

    /** A4 vertical: 0,8 cm de margen y 2 cm arriba, que es donde vive la cabecera. */
    static final int ANCHO_PAGINA = 11906;
    static final int ALTO_PAGINA = 16838;
    static final int MARGEN = 454;
    static final int MARGEN_SUPERIOR = 1134;
    static final int DISTANCIA_CABECERA = 340;
    static final int COLUMNAS = 2;
    static final int FILAS = 3;
    static final int FOTOS_POR_PAGINA = COLUMNAS * FILAS;
    static final int ANCHO_UTIL = ANCHO_PAGINA - 2 * MARGEN;
    static final int ALTO_UTIL = ALTO_PAGINA - MARGEN_SUPERIOR - MARGEN;
    /** Los separadores de 1 pt y un margen para que Word no se pase de página. */
    static final int RESERVA = 170;
    static final int ANCHO_CELDA = ANCHO_UTIL / COLUMNAS;
    static final int ALTO_CELDA = (ALTO_UTIL - RESERVA) / FILAS;
    /** Aire alrededor de cada foto: 0,1 cm. */
    static final int HUECO = 57;
    private static final String ESTILO_TITULO = "Title";
    private static final int TAMANO_TITULO = 22;

    public byte[] generar(FotosCorte fotos) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            WordCorte.configurarPagina(doc, ANCHO_PAGINA, ALTO_PAGINA, false,
                    MARGEN_SUPERIOR, MARGEN, MARGEN, DISTANCIA_CABECERA);
            escribirCabecera(doc, fotos);
            List<Imagen> imagenes = fotos.fotos();
            int paginas = (imagenes.size() + FOTOS_POR_PAGINA - 1) / FOTOS_POR_PAGINA;
            for (int pagina = 0; pagina < paginas; pagina++) {
                escribirRejilla(doc, imagenes.subList(pagina * FOTOS_POR_PAGINA,
                        Math.min(imagenes.size(), (pagina + 1) * FOTOS_POR_PAGINA)));
                WordCorte.separador(doc, pagina < paginas - 1);
            }
            return WordCorte.escribir(doc);
        }
    }

    private void escribirCabecera(XWPFDocument doc, FotosCorte fotos) {
        crearEstiloTitulo(doc);
        XWPFHeader cabecera = doc.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph titulo = cabecera.createParagraph();
        titulo.setStyle(ESTILO_TITULO);
        WordCorte.sinEspacio(titulo);
        WordCorte.texto(titulo, fotos.titulo(), TAMANO_TITULO, false, "000000");
    }

    /**
     * El estilo integrado "Title" de Word: con ese id y ese nombre Word lo
     * reconoce como el suyo y lo enseña traducido (Título). Un documento
     * nuevo de POI no trae estilos, así que hay que declararlo.
     */
    private static void crearEstiloTitulo(XWPFDocument doc) {
        XWPFStyles estilos = doc.createStyles();
        if (estilos.styleExist(ESTILO_TITULO)) {
            return;
        }
        CTStyle estilo = CTStyle.Factory.newInstance();
        estilo.setStyleId(ESTILO_TITULO);
        estilo.setType(STStyleType.PARAGRAPH);
        estilo.addNewName().setVal(ESTILO_TITULO);
        estilo.addNewQFormat();
        estilo.addNewRPr().addNewSz().setVal(java.math.BigInteger.valueOf(2L * TAMANO_TITULO));
        estilos.addStyle(new XWPFStyle(estilo, estilos));
    }

    private void escribirRejilla(XWPFDocument doc, List<Imagen> imagenes) throws IOException {
        int filas = (imagenes.size() + COLUMNAS - 1) / COLUMNAS;
        int[] anchos = new int[COLUMNAS];
        Arrays.fill(anchos, ANCHO_CELDA);
        XWPFTable rejilla = WordCorte.tabla(doc, filas, anchos);
        // Margen solo a los lados: Word suma el de arriba y el de abajo a las
        // filas de alto exacto y la tercera fila saltaría de hoja. El aire de
        // arriba y abajo ya lo deja la foto, que se encaja más baja que la celda.
        rejilla.setCellMargins(0, HUECO, 0, HUECO);
        for (int fila = 0; fila < filas; fila++) {
            WordCorte.altoExacto(rejilla.getRow(fila), ALTO_CELDA);
        }
        for (int i = 0; i < imagenes.size(); i++) {
            XWPFTableCell celda = rejilla.getRow(i / COLUMNAS).getCell(i % COLUMNAS);
            celda.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            XWPFParagraph parrafo = WordCorte.primerParrafo(celda);
            parrafo.setAlignment(ParagraphAlignment.CENTER);
            WordCorte.imagen(parrafo, imagenes.get(i),
                    ANCHO_CELDA - 2 * HUECO, ALTO_CELDA - 2 * HUECO - 60);
        }
    }
}

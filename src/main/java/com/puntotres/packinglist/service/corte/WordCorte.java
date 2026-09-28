package com.puntotres.packinglist.service.corte;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Arrays;

import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.apache.poi.xwpf.usermodel.TableWidthType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;

/**
 * Lo que comparten los dos Word del corte: página, tablas de medidas
 * exactas, textos y fotos. Todo en twips (veinteavos de punto), que es como
 * mide Word la página y las tablas, salvo las imágenes, que van en EMU.
 *
 * Las tablas van con medidas y alturas EXACTAS a propósito: es lo único que
 * garantiza que una orden de corte o una hoja de fotos ocupe exactamente una
 * página, en vez de dejar que Word reparta y empuje media tabla a la
 * siguiente.
 */
final class WordCorte {

    static final String FUENTE = "Calibri";
    /** Word mide las imágenes en EMU: 635 por twip. */
    static final int EMU_POR_TWIP = 635;
    /** Alto de los párrafos separadores: 1 pt, lo mínimo que Word respeta. */
    static final int ALTO_SEPARADOR = 20;

    private WordCorte() {
    }

    static void configurarPagina(XWPFDocument doc, int ancho, int alto, boolean apaisado,
                                 int arriba, int lados, int abajo, int cabecera) {
        CTBody cuerpo = doc.getDocument().getBody();
        CTSectPr seccion = cuerpo.isSetSectPr() ? cuerpo.getSectPr() : cuerpo.addNewSectPr();
        CTPageSz tamano = seccion.isSetPgSz() ? seccion.getPgSz() : seccion.addNewPgSz();
        tamano.setW(BigInteger.valueOf(ancho));
        tamano.setH(BigInteger.valueOf(alto));
        if (apaisado) {
            tamano.setOrient(STPageOrientation.LANDSCAPE);
        }
        CTPageMar margen = seccion.isSetPgMar() ? seccion.getPgMar() : seccion.addNewPgMar();
        margen.setTop(BigInteger.valueOf(arriba));
        margen.setBottom(BigInteger.valueOf(abajo));
        margen.setLeft(BigInteger.valueOf(lados));
        margen.setRight(BigInteger.valueOf(lados));
        margen.setHeader(BigInteger.valueOf(cabecera));
        margen.setFooter(BigInteger.valueOf(cabecera));
    }

    /** Una tabla sin bordes, de anchos fijos: Word no la ensancha según el contenido. */
    static XWPFTable tabla(XWPFDocument doc, int filas, int... anchos) {
        XWPFTable tabla = doc.createTable(filas, anchos.length);
        tabla.removeBorders();
        tabla.setWidth(Arrays.stream(anchos).sum());
        tabla.setWidthType(TableWidthType.DXA);
        CTTblPr propiedades = tabla.getCTTbl().getTblPr();
        CTTblLayoutType disposicion = propiedades.isSetTblLayout()
                ? propiedades.getTblLayout() : propiedades.addNewTblLayout();
        disposicion.setType(STTblLayoutType.FIXED);
        CTTblGrid rejilla = tabla.getCTTbl().getTblGrid() != null
                ? tabla.getCTTbl().getTblGrid() : tabla.getCTTbl().addNewTblGrid();
        while (rejilla.sizeOfGridColArray() > 0) {
            rejilla.removeGridCol(0);
        }
        for (int ancho : anchos) {
            rejilla.addNewGridCol().setW(BigInteger.valueOf(ancho));
        }
        for (XWPFTableRow fila : tabla.getRows()) {
            for (int columna = 0; columna < anchos.length; columna++) {
                XWPFTableCell celda = fila.getCell(columna);
                celda.setWidth(String.valueOf(anchos[columna]));
                celda.setWidthType(TableWidthType.DXA);
            }
        }
        return tabla;
    }

    /** Alto exacto y sin partirse entre páginas. */
    static void altoExacto(XWPFTableRow fila, int twips) {
        fila.setHeight(twips);
        fila.setHeightRule(TableRowHeightRule.EXACT);
        fila.setCantSplitRow(true);
    }

    static XWPFParagraph primerParrafo(XWPFTableCell celda) {
        XWPFParagraph parrafo = celda.getParagraphs().isEmpty()
                ? celda.addParagraph() : celda.getParagraphs().get(0);
        sinEspacio(parrafo);
        return parrafo;
    }

    static XWPFParagraph parrafo(XWPFTableCell celda, ParagraphAlignment alineacion) {
        XWPFParagraph parrafo = celda.addParagraph();
        sinEspacio(parrafo);
        parrafo.setAlignment(alineacion);
        return parrafo;
    }

    static void sinEspacio(XWPFParagraph parrafo) {
        parrafo.setSpacingBefore(0);
        parrafo.setSpacingAfter(0);
    }

    static XWPFRun texto(XWPFParagraph parrafo, String texto, int tamano, boolean negrita,
                         String color) {
        XWPFRun run = parrafo.createRun();
        run.setFontFamily(FUENTE);
        run.setFontSize(tamano);
        run.setBold(negrita);
        if (color != null) {
            run.setColor(color);
        }
        run.setText(texto == null ? "" : texto);
        return run;
    }

    /** La foto encajada en el hueco sin deformarla: manda el lado que antes llega al borde. */
    static void imagen(XWPFParagraph parrafo, Imagen imagen, int anchoMaximo, int altoMaximo)
            throws IOException {
        double escala = Math.min((double) anchoMaximo / imagen.ancho(),
                (double) altoMaximo / imagen.alto());
        int ancho = (int) Math.floor(imagen.ancho() * escala) * EMU_POR_TWIP;
        int alto = (int) Math.floor(imagen.alto() * escala) * EMU_POR_TWIP;
        try (InputStream datos = new ByteArrayInputStream(imagen.jpeg())) {
            parrafo.createRun().addPicture(datos, PictureType.JPEG, "foto.jpg", ancho, alto);
        } catch (InvalidFormatException e) {
            throw new IOException("no se ha podido meter la foto en el Word", e);
        }
    }

    static void bordeCaja(XWPFTableCell celda, int grosor, String color) {
        CTTcPr propiedades = celda.getCTTc().isSetTcPr()
                ? celda.getCTTc().getTcPr() : celda.getCTTc().addNewTcPr();
        CTTcBorders bordes = propiedades.isSetTcBorders()
                ? propiedades.getTcBorders() : propiedades.addNewTcBorders();
        for (CTBorder borde : new CTBorder[] {bordes.addNewTop(), bordes.addNewLeft(),
                bordes.addNewBottom(), bordes.addNewRight()}) {
            borde.setVal(STBorder.SINGLE);
            borde.setSz(BigInteger.valueOf(grosor));
            borde.setSpace(BigInteger.ZERO);
            borde.setColor(color);
        }
    }

    /** Un párrafo vacío de alto exacto: el aire entre dos tablas, que sin él Word fusiona. */
    static XWPFParagraph parrafoDeAlto(XWPFDocument doc, int twips) {
        XWPFParagraph parrafo = doc.createParagraph();
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

    /**
     * El párrafo de 1 pt que cierra una página, con su salto si hay otra
     * detrás. Tan bajo porque lo que va detrás del salto (la marca de
     * párrafo) abre la página siguiente y le roba ese alto.
     */
    static void separador(XWPFDocument doc, boolean saltoDePagina) {
        XWPFRun run = parrafoDeAlto(doc, ALTO_SEPARADOR).createRun();
        run.setFontSize(1);
        if (saltoDePagina) {
            run.addBreak(BreakType.PAGE);
        }
    }

    static byte[] escribir(XWPFDocument doc) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        doc.write(salida);
        return salida.toByteArray();
    }
}

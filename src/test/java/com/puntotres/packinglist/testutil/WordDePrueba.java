package com.puntotres.packinglist.testutil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType;

/** Lecturas de un Word reabierto con POI, para los tests de los documentos del corte. */
public final class WordDePrueba {

    private WordDePrueba() {
    }

    /** Las imágenes de una tabla, en orden de celda. */
    public static List<XWPFPicture> fotosDe(XWPFTable tabla) {
        List<XWPFPicture> fotos = new ArrayList<>();
        for (XWPFTableRow fila : tabla.getRows()) {
            for (XWPFTableCell celda : fila.getTableCells()) {
                for (XWPFParagraph parrafo : celda.getParagraphs()) {
                    for (XWPFRun run : parrafo.getRuns()) {
                        fotos.addAll(run.getEmbeddedPictures());
                    }
                }
            }
        }
        return fotos;
    }

    /** Los saltos de página del cuerpo (fuera de las tablas). */
    public static int saltosDePagina(XWPFDocument doc) {
        int saltos = 0;
        for (XWPFParagraph parrafo : doc.getParagraphs()) {
            for (XWPFRun run : parrafo.getRuns()) {
                for (CTBr salto : run.getCTR().getBrList()) {
                    if (salto.getType() == STBrType.PAGE) {
                        saltos++;
                    }
                }
            }
        }
        return saltos;
    }

    /** Todo el texto, tablas y cabeceras incluidas. */
    public static String texto(XWPFDocument doc) {
        try (XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Ancho de una imagen en EMU, tal como lo guarda Word. */
    public static long anchoEmu(XWPFPicture foto) {
        return foto.getCTPicture().getSpPr().getXfrm().getExt().getCx();
    }

    /** Alto de una imagen en EMU. */
    public static long altoEmu(XWPFPicture foto) {
        return foto.getCTPicture().getSpPr().getXfrm().getExt().getCy();
    }
}

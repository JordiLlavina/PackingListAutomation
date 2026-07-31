package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Escribe el excel de etiquetas de UNA destinación de APC a partir de su
 * plantilla real (client-labels/apc-etiquetas-*.xlsx). Se conservan las DOS
 * hojas del libro: en la de cajas se replica el par de etiquetas modelo por
 * caja física y en la de palet la etiqueta modelo por palet, cada bloque
 * con su salto de página (un A4 por par de caja y por palet). El logo de la
 * plantilla se clona en cada bloque copiado leyendo sus anclajes reales.
 */
@Service
public class ApcEtiquetasExcelBuilder {

    /** Datos ya formateados de la etiqueta de una caja. null = en blanco. */
    public record EtiquetaCajaApc(String orderNumber, String livraisonCode, String referencia,
                                  String colour, String size, String piecesBySize,
                                  String colisage, String poidsBrut) {
    }

    /** Datos de la etiqueta de un palet. poidsBrut null = en blanco. */
    public record EtiquetaPaletApc(int numeroCajas, String poidsBrut) {
    }

    public byte[] generar(ApcEtiquetaLayout layout, List<EtiquetaCajaApc> cajas,
                          List<EtiquetaPaletApc> palets) throws IOException {
        try (InputStream plantilla = getClass().getResourceAsStream(layout.rutaPlantilla());
             XSSFWorkbook libro = new XSSFWorkbook(plantilla)) {
            AjusteFuente ajuste = new AjusteFuente(libro);
            escribirHojaCajas(hoja(libro, layout.hojaCajas(), layout), layout, cajas, ajuste);
            escribirHojaPalets(hoja(libro, layout.hojaPalet(), layout), palets);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static XSSFSheet hoja(XSSFWorkbook libro, String nombre, ApcEtiquetaLayout layout) {
        XSSFSheet hoja = libro.getSheet(nombre);
        if (hoja == null) {
            throw new IllegalStateException("La plantilla " + layout.rutaPlantilla()
                    + " no tiene la hoja '" + nombre + "': revisar client-labels/");
        }
        return hoja;
    }

    private static void escribirHojaCajas(XSSFSheet hoja, ApcEtiquetaLayout layout,
                                          List<EtiquetaCajaApc> cajas, AjusteFuente ajuste) {
        if (cajas.isEmpty()) {
            return;
        }
        BloqueEtiquetaModelo modelo = BloqueEtiquetaModelo.capturar(hoja, layout.alturaBloque());
        for (int i = 1; i < cajas.size(); i++) {
            modelo.copiarEn(hoja, i * layout.alturaBloque());
        }
        replicarImagenes(hoja, layout.alturaBloque(), cajas.size());
        for (int i = 0; i < cajas.size(); i++) {
            int base = i * layout.alturaBloque();
            escribirEtiquetaCaja(hoja, layout, base, cajas.get(i), ajuste);
            escribirEtiquetaCaja(hoja, layout, base + layout.offsetSegundaEtiqueta(),
                    cajas.get(i), ajuste);
            if (i < cajas.size() - 1) {
                hoja.setRowBreak(base + layout.alturaBloque() - 1);
            }
        }
    }

    private static void escribirEtiquetaCaja(XSSFSheet hoja, ApcEtiquetaLayout layout,
                                             int base, EtiquetaCajaApc etiqueta,
                                             AjusteFuente ajuste) {
        escribir(hoja, base + layout.filaOrder(), etiqueta.orderNumber());
        escribir(hoja, base + layout.filaLivraison(), etiqueta.livraisonCode());
        // Estas tres pueden llevar varios artículos concatenados y crecer.
        ajuste.ajustar(escribir(hoja, base + layout.filaReferencia(), etiqueta.referencia()));
        ajuste.ajustar(escribir(hoja, base + layout.filaColor(), etiqueta.colour()));
        escribir(hoja, base + layout.filaTalla(), etiqueta.size());
        ajuste.ajustar(escribir(hoja, base + layout.filaPiezas(), etiqueta.piecesBySize()));
        escribir(hoja, base + layout.filaColisage(), etiqueta.colisage());
        escribir(hoja, base + layout.filaPeso(), etiqueta.poidsBrut());
    }

    private static Cell escribir(XSSFSheet hoja, int fila, String valor) {
        XSSFRow f = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell celda = f.getCell(ApcEtiquetaLayout.COL_VALOR) != null
                ? f.getCell(ApcEtiquetaLayout.COL_VALOR)
                : f.createCell(ApcEtiquetaLayout.COL_VALOR);
        if (valor == null || valor.isBlank()) {
            celda.setBlank();
        } else {
            celda.setCellValue(valor);
        }
        return celda;
    }

    /**
     * Clona las imágenes de la plantilla (el logo del cliente) en cada
     * bloque copiado, desplazando sus anclajes reales i*alturaBloque filas:
     * así no hay que hardcodear EMUs por plantilla como en AMI.
     */
    private static void replicarImagenes(XSSFSheet hoja, int alturaBloque, int numBloques) {
        XSSFDrawing dibujo = hoja.getDrawingPatriarch();
        if (dibujo == null || numBloques <= 1) {
            return;
        }
        List<XSSFPicture> originales = new ArrayList<>();
        for (XSSFShape forma : dibujo.getShapes()) {
            if (forma instanceof XSSFPicture imagen) {
                originales.add(imagen);
            }
        }
        for (XSSFPicture imagen : originales) {
            XSSFClientAnchor origen = imagen.getClientAnchor();
            int indice = hoja.getWorkbook().addPicture(imagen.getPictureData().getData(),
                    imagen.getPictureData().getPictureType());
            for (int i = 1; i < numBloques; i++) {
                XSSFClientAnchor ancla = new XSSFClientAnchor(
                        origen.getDx1(), origen.getDy1(), origen.getDx2(), origen.getDy2(),
                        origen.getCol1(), origen.getRow1() + i * alturaBloque,
                        origen.getCol2(), origen.getRow2() + i * alturaBloque);
                ancla.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
                dibujo.createPicture(ancla, indice);
            }
        }
    }

    private static void escribirHojaPalets(XSSFSheet hoja, List<EtiquetaPaletApc> palets) {
        limpiarContadorManual(hoja);
        if (palets.isEmpty()) {
            // Sin palets no se sabe qué imprimir: la etiqueta modelo queda
            // con los valores en blanco (el generador avisa).
            escribir(hoja, ApcEtiquetaLayout.FILA_PALET_NUM_CAJAS, null);
            escribir(hoja, ApcEtiquetaLayout.FILA_PALET_PESO, null);
            return;
        }
        BloqueEtiquetaModelo modelo =
                BloqueEtiquetaModelo.capturar(hoja, ApcEtiquetaLayout.ALTURA_BLOQUE_PALET);
        for (int i = 1; i < palets.size(); i++) {
            modelo.copiarEn(hoja, i * ApcEtiquetaLayout.ALTURA_BLOQUE_PALET);
        }
        replicarImagenes(hoja, ApcEtiquetaLayout.ALTURA_BLOQUE_PALET, palets.size());
        for (int i = 0; i < palets.size(); i++) {
            int base = i * ApcEtiquetaLayout.ALTURA_BLOQUE_PALET;
            escribirNumero(hoja, base + ApcEtiquetaLayout.FILA_PALET_NUM_CAJAS,
                    palets.get(i).numeroCajas());
            escribir(hoja, base + ApcEtiquetaLayout.FILA_PALET_PESO,
                    palets.get(i).poidsBrut());
            if (i < palets.size() - 1) {
                hoja.setRowBreak(base + ApcEtiquetaLayout.ALTURA_BLOQUE_PALET - 1);
            }
        }
    }

    /**
     * La fila 1 de la hoja de palet trae una celda suelta con un contador
     * apuntado a mano (E1/C1/D1 según plantilla) que no debe replicarse.
     */
    private static void limpiarContadorManual(XSSFSheet hoja) {
        if (hoja.getRow(0) != null) {
            hoja.getRow(0).forEach(Cell::setBlank);
        }
    }

    private static void escribirNumero(XSSFSheet hoja, int fila, int valor) {
        XSSFRow f = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell celda = f.getCell(ApcEtiquetaLayout.COL_VALOR) != null
                ? f.getCell(ApcEtiquetaLayout.COL_VALOR)
                : f.createCell(ApcEtiquetaLayout.COL_VALOR);
        celda.setCellValue(valor);
    }
}

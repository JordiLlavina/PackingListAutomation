package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Escribe el excel de etiquetas de caja de UNA destinación de AMI a partir
 * de la plantilla real (client-labels/ami-etiquetas-template.xlsx, copia
 * del "ETIQUETA CAJA AMI.xlsx" del cliente).
 *
 * La plantilla trae una hoja por destinación con un par de etiquetas
 * modelo y sus imágenes de EJEMPLO; aquí se conserva solo la hoja pedida,
 * se limpian esas imágenes, se replica el bloque modelo para cada caja
 * (estilos, altos de fila y celdas combinadas incluidos) y se insertan el
 * código de barras generado y, en JAPAN, la imagen-dirección extraída de
 * la propia plantilla. Cada par lleva su salto de página: un A4 por caja.
 */
@Service
public class AmiEtiquetasExcelBuilder {

    private static final String RUTA_PLANTILLA = "/client-labels/ami-etiquetas-template.xlsx";

    /**
     * Los datos ya formateados de la etiqueta de una caja física. null =
     * celda en blanco (y sin código de barras si falta orderNumber).
     */
    public record EtiquetaCaja(String temporada, String referencia, String colorCode,
                               String talla, String cantidad, String pesoBruto,
                               String parcel, String orderNumber) {
    }

    public byte[] generar(AmiEtiquetaLayout layout, List<EtiquetaCaja> etiquetas)
            throws IOException {
        try (InputStream plantilla = getClass().getResourceAsStream(RUTA_PLANTILLA);
             XSSFWorkbook libro = new XSSFWorkbook(plantilla)) {

            byte[] direccionJapan = extraerPngDireccion(libro);
            dejarSoloLaHoja(libro, layout.nombreHoja());
            XSSFSheet hoja = libro.getSheetAt(0);
            limpiarImagenesDeEjemplo(hoja);

            BloqueEtiquetaModelo modelo = BloqueEtiquetaModelo.capturar(hoja, layout.alturaBloque());
            for (int i = 1; i < etiquetas.size(); i++) {
                modelo.copiarEn(hoja, i * layout.alturaBloque());
            }
            for (int i = 0; i < etiquetas.size(); i++) {
                int base = i * layout.alturaBloque();
                escribirEtiqueta(hoja, layout, base, etiquetas.get(i));
                escribirEtiqueta(hoja, layout, base + layout.offsetSegundaEtiqueta(),
                        etiquetas.get(i));
                insertarImagenes(libro, hoja, layout, base, etiquetas.get(i), direccionJapan);
                if (i < etiquetas.size() - 1) {
                    hoja.setRowBreak(base + layout.alturaBloque() - 1);
                }
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    // --- pasos ---

    /** La dirección de entrega de JAPAN va como imagen: el único PNG del libro. */
    private static byte[] extraerPngDireccion(XSSFWorkbook libro) {
        for (XSSFPictureData imagen : libro.getAllPictures()) {
            if (imagen.getPictureType() == Workbook.PICTURE_TYPE_PNG) {
                return imagen.getData();
            }
        }
        return null;
    }

    private static void dejarSoloLaHoja(XSSFWorkbook libro, String nombreHoja) {
        for (int i = libro.getNumberOfSheets() - 1; i >= 0; i--) {
            if (!libro.getSheetName(i).equals(nombreHoja)) {
                libro.removeSheetAt(i);
            }
        }
        if (libro.getNumberOfSheets() != 1) {
            throw new IllegalStateException("La plantilla de etiquetas AMI no tiene la hoja '"
                    + nombreHoja + "': revisar client-labels/ami-etiquetas-template.xlsx");
        }
        libro.setActiveSheet(0);
    }

    /** Quita los anclajes de las imágenes de ejemplo (los gifs de barcode y el png). */
    private static void limpiarImagenesDeEjemplo(XSSFSheet hoja) {
        XSSFDrawing dibujo = hoja.getDrawingPatriarch();
        if (dibujo == null) {
            return;
        }
        var ct = dibujo.getCTDrawing();
        while (ct.sizeOfOneCellAnchorArray() > 0) {
            ct.removeOneCellAnchor(0);
        }
        while (ct.sizeOfTwoCellAnchorArray() > 0) {
            ct.removeTwoCellAnchor(0);
        }
    }

    private static void escribirEtiqueta(XSSFSheet hoja, AmiEtiquetaLayout layout,
                                         int base, EtiquetaCaja etiqueta) {
        // El order number va también como texto bajo su encabezado (la
        // plantilla trae un valor de ejemplo que hay que pisar siempre).
        escribir(hoja, base + layout.filaOrderNumber(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.orderNumber());
        // La temporada conserva el estilo de la plantilla (rojo en AMI): es
        // el aspecto que quiere el cliente, no un placeholder a corregir.
        escribir(hoja, base + layout.filaTemporada(),
                AmiEtiquetaLayout.COL_TEMPORADA, etiqueta.temporada());
        escribir(hoja, base + layout.filaReferencia(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.referencia());
        escribir(hoja, base + layout.filaColor(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.colorCode());
        escribir(hoja, base + layout.filaTalla(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.talla());
        escribir(hoja, base + layout.filaCantidad(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.cantidad());
        escribir(hoja, base + layout.filaPeso(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.pesoBruto());
        escribir(hoja, base + layout.filaParcel(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.parcel());
    }

    private static Cell escribir(XSSFSheet hoja, int fila, int col, String valor) {
        XSSFRow f = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell celda = f.getCell(col) != null ? f.getCell(col) : f.createCell(col);
        if (valor == null || valor.isBlank()) {
            celda.setBlank();
        } else {
            celda.setCellValue(valor);
        }
        return celda;
    }

    private void insertarImagenes(XSSFWorkbook libro, XSSFSheet hoja, AmiEtiquetaLayout layout,
                                  int base, EtiquetaCaja etiqueta, byte[] direccionJapan) {
        XSSFDrawing dibujo = hoja.createDrawingPatriarch();
        int[] offsets = {0, layout.offsetSegundaEtiqueta()};
        byte[] barcode = etiqueta.orderNumber() == null || etiqueta.orderNumber().isBlank()
                ? null
                : CodigoBarrasCode128.png(etiqueta.orderNumber());
        for (int offset : offsets) {
            if (barcode != null) {
                int indice = libro.addPicture(barcode, Workbook.PICTURE_TYPE_PNG);
                dibujo.createPicture(anclar(hoja, layout.po(), base + offset), indice);
            }
            if (direccionJapan != null && "AMI JAPAN".equals(layout.nombreHoja())) {
                int indice = libro.addPicture(direccionJapan, Workbook.PICTURE_TYPE_PNG);
                dibujo.createPicture(
                        anclar(hoja, AmiEtiquetaLayout.JAPAN_DIRECCION, base + offset), indice);
            }
        }
    }

    /** Traduce un anclaje relativo al bloque a un anclaje de tamaño fijo de POI. */
    private static XSSFClientAnchor anclar(XSSFSheet hoja, AnclajeBloque anclaje, int base) {
        return AnclajeImagen.fijo(hoja, AmiEtiquetaLayout.COL_BARCODE, anclaje.dx(),
                base + anclaje.fila(), anclaje.dy(), anclaje.cx(), anclaje.cy());
    }
}

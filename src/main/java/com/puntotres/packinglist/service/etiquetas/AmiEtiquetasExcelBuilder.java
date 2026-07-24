package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
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

            BloqueModelo modelo = BloqueModelo.capturar(hoja, layout.alturaBloque());
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
        escribir(hoja, base + layout.filaTemporada(), AmiEtiquetaLayout.COL_TEMPORADA,
                etiqueta.temporada());
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

    private static void escribir(XSSFSheet hoja, int fila, int col, String valor) {
        XSSFRow f = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell celda = f.getCell(col) != null ? f.getCell(col) : f.createCell(col);
        if (valor == null || valor.isBlank()) {
            celda.setBlank();
        } else {
            celda.setCellValue(valor);
        }
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
                dibujo.createPicture(anclaje(hoja, AmiEtiquetaLayout.COL_BARCODE,
                        layout.dxBarcode(), base + offset + layout.filaBarcode(),
                        layout.dyBarcode(), layout.cxBarcode(), layout.cyBarcode()), indice);
            }
            if (direccionJapan != null && "AMI JAPAN".equals(layout.nombreHoja())) {
                int indice = libro.addPicture(direccionJapan, Workbook.PICTURE_TYPE_PNG);
                dibujo.createPicture(anclaje(hoja, AmiEtiquetaLayout.COL_BARCODE,
                        AmiEtiquetaLayout.JAPAN_DIRECCION_DX,
                        base + offset + AmiEtiquetaLayout.JAPAN_DIRECCION_FILA,
                        AmiEtiquetaLayout.JAPAN_DIRECCION_DY,
                        AmiEtiquetaLayout.JAPAN_DIRECCION_CX,
                        AmiEtiquetaLayout.JAPAN_DIRECCION_CY), indice);
            }
        }
    }

    /**
     * Anclaje de dos celdas equivalente al oneCellAnchor de la plantilla:
     * desde (col,fila)+offset EMU, con tamaño fijo (cx,cy) EMU repartido
     * sobre las columnas/filas siguientes según sus anchos reales.
     */
    private static XSSFClientAnchor anclaje(XSSFSheet hoja, int col, long dx,
                                            int fila, long dy, long cx, long cy) {
        int col2 = col;
        long xRestante = dx + cx;
        while (xRestante > anchoColumnaEmu(hoja, col2)) {
            xRestante -= anchoColumnaEmu(hoja, col2);
            col2++;
        }
        int fila2 = fila;
        long yRestante = dy + cy;
        while (yRestante > altoFilaEmu(hoja, fila2)) {
            yRestante -= altoFilaEmu(hoja, fila2);
            fila2++;
        }
        XSSFClientAnchor ancla = new XSSFClientAnchor((int) dx, (int) dy,
                (int) xRestante, (int) yRestante, col, fila, col2, fila2);
        ancla.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
        return ancla;
    }

    private static long anchoColumnaEmu(XSSFSheet hoja, int col) {
        return Units.columnWidthToEMU(hoja.getColumnWidth(col));
    }

    private static long altoFilaEmu(XSSFSheet hoja, int fila) {
        float puntos = hoja.getRow(fila) != null
                ? hoja.getRow(fila).getHeightInPoints()
                : hoja.getDefaultRowHeightInPoints();
        return Units.toEMU(puntos);
    }

    /**
     * El par de etiquetas modelo de la plantilla: valores, estilos, altos
     * de fila y celdas combinadas de las primeras alturaBloque filas,
     * capturados antes de escribir nada para poder replicarlos por caja.
     */
    private record BloqueModelo(List<FilaModelo> filas, List<CellRangeAddress> merges,
                                int altura) {

        private record CeldaModelo(int col, CellStyle estilo, CellType tipo, String texto) {
        }

        private record FilaModelo(int fila, float altoPuntos, boolean altoPersonalizado,
                                  List<CeldaModelo> celdas) {
        }

        static BloqueModelo capturar(XSSFSheet hoja, int altura) {
            List<FilaModelo> filas = new ArrayList<>();
            for (int i = 0; i < altura; i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) {
                    continue;
                }
                List<CeldaModelo> celdas = new ArrayList<>();
                for (Cell celda : fila) {
                    celdas.add(new CeldaModelo(celda.getColumnIndex(), celda.getCellStyle(),
                            celda.getCellType(),
                            celda.getCellType() == CellType.STRING
                                    ? celda.getStringCellValue() : null));
                }
                filas.add(new FilaModelo(i, fila.getHeightInPoints(),
                        ((XSSFRow) fila).getCTRow().getCustomHeight(), celdas));
            }
            List<CellRangeAddress> merges = new ArrayList<>();
            for (CellRangeAddress merge : hoja.getMergedRegions()) {
                if (merge.getLastRow() < altura) {
                    merges.add(merge);
                }
            }
            return new BloqueModelo(filas, merges, altura);
        }

        void copiarEn(XSSFSheet hoja, int filaDestino) {
            for (FilaModelo modelo : filas) {
                XSSFRow fila = hoja.createRow(filaDestino + modelo.fila());
                if (modelo.altoPersonalizado()) {
                    fila.setHeightInPoints(modelo.altoPuntos());
                }
                for (CeldaModelo celdaModelo : modelo.celdas()) {
                    Cell celda = fila.createCell(celdaModelo.col());
                    // Mismo libro: la referencia de estilo se comparte, sin clonar.
                    celda.setCellStyle(celdaModelo.estilo());
                    if (celdaModelo.texto() != null) {
                        celda.setCellValue(celdaModelo.texto());
                    }
                }
            }
            for (CellRangeAddress merge : merges) {
                hoja.addMergedRegion(new CellRangeAddress(
                        merge.getFirstRow() + filaDestino, merge.getLastRow() + filaDestino,
                        merge.getFirstColumn(), merge.getLastColumn()));
            }
        }
    }
}

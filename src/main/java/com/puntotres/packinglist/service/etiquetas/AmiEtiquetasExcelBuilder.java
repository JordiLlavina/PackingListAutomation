package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFShape;
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
 * (estilos, altos de fila y celdas combinadas incluidos) y se insertan
 * la imagen compuesta del artículo y el Code 128 del EAN128 y, en JAPAN, la
 * imagen-dirección extraída de la propia plantilla. Cada par lleva su salto
 * de página: un A4 por caja.
 */
@Service
public class AmiEtiquetasExcelBuilder {

    private static final String RUTA_PLANTILLA = "/client-labels/ami-etiquetas-template.xlsx";

    /**
     * Los datos ya formateados de la etiqueta de una caja física. null =
     * celda en blanco, y sin ese código de barras: sin ean128 no hay su
     * Code 128. articulo son los cuatro textos y el EAN-13 del PRIMER
     * artículo de la caja, los que van dentro de la imagen compuesta; con
     * varios artículos, referencia/colorCode/cantidad vienen concatenados
     * pero la imagen habla solo del primero, que es de quien es su EAN-13.
     */
    public record EtiquetaCaja(String temporada, String referencia, String colorCode,
                               String talla, String cantidad, String pesoBruto,
                               String parcel, String orderNumber, String ean128,
                               EtiquetaArticulo articulo) {
    }

    /** Una imagen ya resuelta: dónde va en el bloque y su índice en el libro. */
    private record ImagenAnclada(AnclajeBloque anclaje, int indice) {
    }

    /** Sin filas extra: la firma que usan los tests y los flujos sin sobrantes. */
    public byte[] generar(AmiEtiquetaLayout layout, List<EtiquetaCaja> etiquetas)
            throws IOException {
        return generar(layout, etiquetas, List.of());
    }

    public byte[] generar(AmiEtiquetaLayout layout, List<EtiquetaCaja> etiquetas,
                          List<FilaCodigoBarrasExtra> filasExtra) throws IOException {
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
            // Un envío repite mucho la misma referencia y el mismo PO: sin
            // esta caché el .xlsx guardaría el mismo PNG una vez por etiqueta.
            Map<String, Integer> imagenesDelLibro = new HashMap<>();
            AjusteFuente ajuste = new AjusteFuente(libro);
            for (int i = 0; i < etiquetas.size(); i++) {
                int base = i * layout.alturaBloque();
                escribirEtiqueta(hoja, layout, base, etiquetas.get(i), ajuste);
                escribirEtiqueta(hoja, layout, base + layout.offsetSegundaEtiqueta(),
                        etiquetas.get(i), ajuste);
                insertarImagenes(libro, hoja, layout, base, etiquetas.get(i), direccionJapan,
                        imagenesDelLibro);
                if (i < etiquetas.size() - 1) {
                    hoja.setRowBreak(base + layout.alturaBloque() - 1);
                }
            }

            // La hoja extra va DESPUÉS de dejarSoloLaHoja, que se lleva por
            // delante cualquier otra hoja del libro.
            HojaCodigosBarrasExtra.escribir(libro, filasExtra);

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    // --- pasos ---

    /**
     * La dirección de entrega de JAPAN va como imagen. No vale coger "el
     * primer PNG del libro": la plantilla nueva trae también el mock de la
     * imagen compuesta, y el orden de getAllPictures() no lo distingue. Se
     * busca en la hoja de JAPAN la imagen anclada en la fila de
     * JAPAN_DIRECCION. null si no está: JAPAN sale sin dirección y el resto
     * de la etiqueta se genera igual.
     */
    private static byte[] extraerPngDireccion(XSSFWorkbook libro) {
        XSSFSheet japan = libro.getSheet(AmiEtiquetaLayout.JAPAN.nombreHoja());
        if (japan == null || japan.getDrawingPatriarch() == null) {
            return null;
        }
        for (XSSFShape forma : japan.getDrawingPatriarch().getShapes()) {
            // getClientAnchor() y no getPreferredSize(): esta última intenta
            // reescalar a un anclaje de dos celdas y revienta con NPE cuando
            // la imagen de la plantilla (como esta) está anclada con una sola
            // celda + tamaño fijo, que es como la trae el cliente.
            if (forma instanceof XSSFPicture imagen
                    && imagen.getClientAnchor().getFrom().getRow()
                            == AmiEtiquetaLayout.JAPAN_DIRECCION.fila()) {
                return imagen.getPictureData().getData();
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

    private void escribirEtiqueta(XSSFSheet hoja, AmiEtiquetaLayout layout, int base,
                                  EtiquetaCaja etiqueta, AjusteFuente ajuste) {
        // El order number va también como texto bajo su encabezado (la
        // plantilla trae un valor de ejemplo que hay que pisar siempre).
        escribir(hoja, base + layout.filaOrderNumber(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.orderNumber());
        // La temporada conserva el estilo de la plantilla (rojo en AMI): es
        // el aspecto que quiere el cliente, no un placeholder a corregir.
        escribir(hoja, base + layout.filaTemporada(),
                AmiEtiquetaLayout.COL_TEMPORADA, etiqueta.temporada());
        // Estas tres pueden llevar varios artículos concatenados y crecer.
        ajuste.ajustar(escribir(hoja, base + layout.filaReferencia(),
                AmiEtiquetaLayout.COL_VALOR, etiqueta.referencia()));
        ajuste.ajustar(escribir(hoja, base + layout.filaColor(),
                AmiEtiquetaLayout.COL_VALOR, etiqueta.colorCode()));
        escribir(hoja, base + layout.filaTalla(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.talla());
        ajuste.ajustar(escribir(hoja, base + layout.filaCantidad(),
                AmiEtiquetaLayout.COL_VALOR, etiqueta.cantidad()));
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
                                  int base, EtiquetaCaja etiqueta, byte[] direccionJapan,
                                  Map<String, Integer> cache) {
        XSSFDrawing dibujo = hoja.createDrawingPatriarch();
        List<ImagenAnclada> imagenes = new ArrayList<>();
        // La imagen compuesta lleva los cuatro textos del artículo y su
        // EAN-13; se genera con la proporción del hueco para que no se
        // deforme. Se cachea por su contenido entero, no solo por el EAN-13:
        // un artículo sin fila en el pedido no tiene EAN-13 y aun así su
        // imagen es distinta de la de otro artículo sin fila.
        if (etiqueta.articulo() != null) {
            imagenes.add(new ImagenAnclada(layout.imagenArticulo(), indice(libro, cache,
                    "ARTICULO:" + etiqueta.articulo(),
                    () -> ImagenEtiquetaArticulo.png(etiqueta.articulo(),
                            layout.imagenArticulo().proporcion()))));
        }
        // El hueco del EAN128 es muy apaisado (casi 6:1) y el código no sale
        // así de serie: se genera ya con la proporción del hueco para que no
        // se estire al encajarlo.
        if (tiene(etiqueta.ean128())) {
            imagenes.add(new ImagenAnclada(layout.ean128(), indice(libro, cache,
                    "EAN128:" + etiqueta.ean128(),
                    () -> CodigoBarrasCode128.png(etiqueta.ean128(),
                            layout.ean128().proporcion()))));
        }
        if (direccionJapan != null && "AMI JAPAN".equals(layout.nombreHoja())) {
            imagenes.add(new ImagenAnclada(AmiEtiquetaLayout.JAPAN_DIRECCION,
                    indice(libro, cache, "DIRECCION", () -> direccionJapan)));
        }

        for (int offset : new int[] {0, layout.offsetSegundaEtiqueta()}) {
            for (ImagenAnclada imagen : imagenes) {
                dibujo.createPicture(
                        anclar(hoja, imagen.anclaje(), base + offset), imagen.indice());
            }
        }
    }

    private static boolean tiene(String valor) {
        return valor != null && !valor.isBlank();
    }

    /** Índice de la imagen en el libro, añadiéndola solo la primera vez. */
    private static int indice(XSSFWorkbook libro, Map<String, Integer> cache,
                              String clave, Supplier<byte[]> png) {
        return cache.computeIfAbsent(clave,
                k -> libro.addPicture(png.get(), Workbook.PICTURE_TYPE_PNG));
    }

    /** Traduce un anclaje relativo al bloque a un anclaje de tamaño fijo de POI. */
    private static XSSFClientAnchor anclar(XSSFSheet hoja, AnclajeBloque anclaje, int base) {
        return AnclajeImagen.fijo(hoja, AmiEtiquetaLayout.COL_BARCODE, anclaje.dx(),
                base + anclaje.fila(), anclaje.dy(), anclaje.cx(), anclaje.cy());
    }
}

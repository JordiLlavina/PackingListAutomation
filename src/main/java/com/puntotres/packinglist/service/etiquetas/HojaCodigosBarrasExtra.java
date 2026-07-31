package com.puntotres.packinglist.service.etiquetas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * La hoja "CODIGOS BARRAS EXTRA": una fila por artículo que no cabe en la
 * etiqueta de su caja, con sus datos y sus códigos de barras escaneables.
 *
 * En una etiqueta solo cabe un par de EAN, así que cuando una caja lleva
 * varios artículos —o un cinturón con varias tallas, que en el pedido son
 * artículos distintos con EAN distinto— los demás códigos no se imprimían en
 * ningún sitio. Aquí van todos, en una sola hoja del mismo libro de la
 * destinación, para imprimirla junto a las etiquetas.
 *
 * <b>No hay plantilla .xlsx</b>: la maquetación son las constantes de esta
 * clase, como en EscandallosExcelBuilder y EtiquetasArticuloExcelBuilder.
 *
 * El código de barras del PO no se repite: es de la caja y ya está en la
 * etiqueta.
 */
public final class HojaCodigosBarrasExtra {

    public static final String NOMBRE_HOJA = "CODIGOS BARRAS EXTRA";

    private static final String[] ENCABEZADOS =
            {"CAJA", "REFERENCE", "COLOR CODE", "SIZE", "QUANTITY", "EAN-13", "EAN128"};
    /** Ancho de cada columna en caracteres; las dos últimas alojan la imagen. */
    private static final int[] ANCHOS_CHARS = {8, 24, 20, 10, 12, 30, 46};

    private static final int COL_EAN13 = 5;
    private static final int COL_EAN128 = 6;

    private static final float ALTO_FILA_PT = 62;
    /** El texto del código va debajo: la imagen ocupa la parte de arriba. */
    private static final long ALTO_IMAGEN_EMU = 520_700;   // ~41 pt
    private static final long ANCHO_EAN13_EMU = 1_478_280;
    private static final long ANCHO_EAN128_EMU = 2_727_960;
    private static final long MARGEN_EMU = 38_100;         // ~3 pt

    private HojaCodigosBarrasExtra() {
    }

    /** Añade la hoja al libro. Sin filas no se crea nada. */
    public static void escribir(XSSFWorkbook libro, List<FilaCodigoBarrasExtra> filas) {
        if (filas.isEmpty()) {
            return;
        }
        XSSFSheet hoja = libro.createSheet(NOMBRE_HOJA);
        for (int col = 0; col < ANCHOS_CHARS.length; col++) {
            hoja.setColumnWidth(col, ANCHOS_CHARS[col] * 256);
        }
        escribirEncabezados(libro, hoja);

        XSSFCellStyle estiloDato = estiloDato(libro);
        XSSFDrawing dibujo = hoja.createDrawingPatriarch();
        // Un envío repite mucho el mismo artículo: sin esta caché el .xlsx
        // guardaría el mismo PNG una vez por fila.
        Map<String, Integer> imagenes = new HashMap<>();
        for (int i = 0; i < filas.size(); i++) {
            escribirFila(libro, hoja, dibujo, estiloDato, imagenes, i + 1, filas.get(i));
        }
        hoja.setRepeatingRows(org.apache.poi.ss.util.CellRangeAddress.valueOf("1:1"));
        hoja.getPrintSetup().setLandscape(true);
        hoja.setFitToPage(true);
        hoja.getPrintSetup().setFitWidth((short) 1);
        hoja.getPrintSetup().setFitHeight((short) 0);
    }

    private static void escribirEncabezados(XSSFWorkbook libro, XSSFSheet hoja) {
        XSSFFont negrita = libro.createFont();
        negrita.setBold(true);
        XSSFCellStyle estilo = libro.createCellStyle();
        estilo.setFont(negrita);
        estilo.setAlignment(HorizontalAlignment.CENTER);
        XSSFRow cabecera = hoja.createRow(0);
        for (int col = 0; col < ENCABEZADOS.length; col++) {
            Cell celda = cabecera.createCell(col);
            celda.setCellValue(ENCABEZADOS[col]);
            celda.setCellStyle(estilo);
        }
    }

    private static XSSFCellStyle estiloDato(XSSFWorkbook libro) {
        XSSFCellStyle estilo = libro.createCellStyle();
        // El texto del código va abajo, bajo su imagen.
        estilo.setVerticalAlignment(VerticalAlignment.BOTTOM);
        return estilo;
    }

    private static void escribirFila(XSSFWorkbook libro, XSSFSheet hoja, XSSFDrawing dibujo,
                                     XSSFCellStyle estilo, Map<String, Integer> imagenes,
                                     int numeroFila, FilaCodigoBarrasExtra fila) {
        XSSFRow row = hoja.createRow(numeroFila);
        row.setHeightInPoints(ALTO_FILA_PT);
        escribir(row, estilo, 0, String.valueOf(fila.numeroCaja()));
        escribir(row, estilo, 1, fila.referencia());
        escribir(row, estilo, 2, fila.colorCode());
        escribir(row, estilo, 3, fila.talla());
        escribir(row, estilo, 4, fila.cantidad());
        escribir(row, estilo, COL_EAN13, fila.ean13());
        escribir(row, estilo, COL_EAN128, fila.ean128());

        // El EAN13 puede llegar inválido: entonces no se dibuja y la fila sale
        // igual (el aviso lo dio ya AmiPedidoExcel), con su texto visible.
        if (tiene(fila.ean13()) && CodigoBarrasEan13.esValido(fila.ean13())) {
            dibujar(libro, hoja, dibujo, imagenes, "EAN13:" + fila.ean13(),
                    () -> CodigoBarrasEan13.png(fila.ean13()).orElseThrow(),
                    COL_EAN13, numeroFila, ANCHO_EAN13_EMU);
        }
        // El EAN128 se genera ya con la proporción de su hueco para que no se
        // estire al encajarlo, como en la etiqueta.
        if (tiene(fila.ean128())) {
            dibujar(libro, hoja, dibujo, imagenes, "EAN128:" + fila.ean128(),
                    () -> CodigoBarrasCode128.png(fila.ean128(),
                            (double) ANCHO_EAN128_EMU / ALTO_IMAGEN_EMU),
                    COL_EAN128, numeroFila, ANCHO_EAN128_EMU);
        }
    }

    private static void escribir(XSSFRow fila, XSSFCellStyle estilo, int col, String valor) {
        Cell celda = fila.createCell(col);
        if (tiene(valor)) {
            celda.setCellValue(valor);
        } else {
            celda.setBlank();
        }
        celda.setCellStyle(estilo);
    }

    private static void dibujar(XSSFWorkbook libro, XSSFSheet hoja, XSSFDrawing dibujo,
                                Map<String, Integer> imagenes, String clave,
                                Supplier<byte[]> png, int columna, int fila, long ancho) {
        int indice = imagenes.computeIfAbsent(clave,
                k -> libro.addPicture(png.get(), Workbook.PICTURE_TYPE_PNG));
        dibujo.createPicture(AnclajeImagen.fijo(hoja, columna, MARGEN_EMU, fila, MARGEN_EMU,
                ancho, ALTO_IMAGEN_EMU), indice);
    }

    private static boolean tiene(String valor) {
        return valor != null && !valor.isBlank();
    }
}

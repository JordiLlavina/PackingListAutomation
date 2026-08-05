package com.puntotres.packinglist.service.etiquetas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * La hoja "CODIGOS BARRAS EXTRA": los artículos que no caben en la etiqueta de
 * su caja, maquetados para imprimir y recortar.
 *
 * En una etiqueta de caja solo cabe un par de códigos, así que cuando una caja
 * lleva varios artículos —o un cinturón con varias tallas, que en el pedido son
 * artículos distintos con EAN distinto— los demás no se imprimían en ningún
 * sitio. Aquí van todos, en una sola hoja del mismo libro de la destinación.
 *
 * Usa la MISMA rejilla que las etiquetas de artículo (RejillaEtiquetas): 10
 * artículos por A4. Cada artículo ocupa un bloque con tres columnas: la caja,
 * la etiqueta con su EAN-13 y la misma etiqueta con su EAN128.
 *
 * <b>No hay plantilla .xlsx</b>: la maquetación son las constantes de esta
 * clase y las de RejillaEtiquetas, medidas de
 * "docs/Etiquetas cajas/AMI ETIQUETAS CAJA - CODE BARRAS EXTRA TEMPLATE.xlsx".
 */
public final class HojaCodigosBarrasExtra {

    public static final String NOMBRE_HOJA = "CODIGOS BARRAS EXTRA";

    private static final String ROTULO_CAJA = "CAJA";
    private static final String ROTULO_DESTINO = "Destinación";

    private static final int COL_CAJA = RejillaEtiquetas.COLUMNAS_IZQUIERDA[0];
    private static final int COL_EAN13 = RejillaEtiquetas.COLUMNAS_IZQUIERDA[1];
    private static final int COL_EAN128 = RejillaEtiquetas.COLUMNAS_IZQUIERDA[2];

    private static final short CUERPO_ROTULO_CAJA = 18;
    private static final short CUERPO_PARCEL = 20;
    private static final short CUERPO_DESTINO = 14;

    /**
     * Anclaje del EAN128 dentro de su bloque, en EMU. El dx es menor que el
     * de la plantilla original: el código, que es ancho, sobresalía por la
     * derecha de la etiqueta, así que se corrió ~3 pt a la izquierda. El
     * tamaño (cx, cy) NO se toca: un código de barras reescalado se lee mal.
     */
    private static final long EAN128_DX = 22860;
    private static final long EAN128_DY = 129540;
    private static final long EAN128_CX = 2118833;
    private static final long EAN128_CY = 352239;

    private HojaCodigosBarrasExtra() {
    }

    /** Añade la hoja al libro. Sin filas no se crea nada. */
    public static void escribir(XSSFWorkbook libro, List<FilaCodigoBarrasExtra> filas) {
        if (filas.isEmpty()) {
            return;
        }
        XSSFSheet hoja = RejillaEtiquetas.crearHojaMaquetada(libro, NOMBRE_HOJA, filas.size());
        BloqueEtiquetaArticulo bloques = new BloqueEtiquetaArticulo(libro);
        Estilos estilos = new Estilos(libro);
        XSSFDrawing dibujo = hoja.createDrawingPatriarch();
        // Un envío repite mucho el mismo artículo: sin esta caché el .xlsx
        // guardaría el mismo PNG una vez por bloque.
        Map<String, Integer> imagenes = new HashMap<>();
        for (int i = 0; i < filas.size(); i++) {
            escribirBloque(libro, hoja, bloques, estilos, dibujo, imagenes,
                    RejillaEtiquetas.filaBase(i), filas.get(i));
        }
    }

    // --- pasos ---

    private static void escribirBloque(XSSFWorkbook libro, XSSFSheet hoja,
                                       BloqueEtiquetaArticulo bloques, Estilos estilos,
                                       XSSFDrawing dibujo, Map<String, Integer> imagenes,
                                       int base, FilaCodigoBarrasExtra fila) {
        caja(hoja, estilos, base, fila);
        bloques.escribirTextos(hoja, base, COL_EAN13, fila.articulo());
        bloques.escribirTextos(hoja, base, COL_EAN128, fila.articulo());

        String ean13 = fila.articulo().ean13();
        if (CodigoBarrasEan13.esValido(ean13)) {
            dibujo.createPicture(
                    BloqueEtiquetaArticulo.anclajeCodigo(hoja, base, COL_EAN13),
                    indice(libro, imagenes, "EAN13:" + ean13,
                            () -> CodigoBarrasEan13.png(ean13).orElseThrow()));
        }
        if (fila.ean128() != null && !fila.ean128().isBlank()) {
            dibujo.createPicture(
                    AnclajeImagen.fijo(hoja, COL_EAN128, EAN128_DX,
                            base + BloqueEtiquetaArticulo.BARCODE_OFFSET_FILA, EAN128_DY,
                            EAN128_CX, EAN128_CY),
                    indice(libro, imagenes, "EAN128:" + fila.ean128(),
                            () -> CodigoBarrasCode128.png(fila.ean128(),
                                    (double) EAN128_CX / EAN128_CY)));
        }
    }

    /** La columna de la izquierda: de qué caja y de qué destinación es. */
    private static void caja(XSSFSheet hoja, Estilos estilos, int base,
                             FilaCodigoBarrasExtra fila) {
        parDeCeldas(hoja, estilos.negrita(CUERPO_ROTULO_CAJA), base, COL_CAJA, ROTULO_CAJA);
        parDeCeldas(hoja, estilos.negrita(CUERPO_PARCEL), base, COL_CAJA + 1, fila.parcel());
        parDeCeldas(hoja, estilos.negrita(CUERPO_DESTINO), base + 2, COL_CAJA,
                ROTULO_DESTINO);
        parDeCeldas(hoja, estilos.negrita(CUERPO_DESTINO), base + 2, COL_CAJA + 1,
                fila.destino());
    }

    /** Dos filas combinadas en vertical con un texto centrado. */
    private static void parDeCeldas(XSSFSheet hoja, XSSFCellStyle estilo, int fila,
                                    int columna, String texto) {
        for (int i = 0; i < 2; i++) {
            if (hoja.getRow(fila + i) == null) {
                hoja.createRow(fila + i);
            }
            Cell celda = hoja.getRow(fila + i).createCell(columna);
            celda.setCellStyle(estilo);
        }
        Cell celda = hoja.getRow(fila).getCell(columna);
        if (texto == null || texto.isBlank()) {
            celda.setBlank();
        } else {
            celda.setCellValue(texto);
        }
        hoja.addMergedRegion(new CellRangeAddress(fila, fila + 1, columna, columna));
    }

    /** Índice de la imagen en el libro, añadiéndola solo la primera vez. */
    private static int indice(XSSFWorkbook libro, Map<String, Integer> cache,
                              String clave, Supplier<byte[]> png) {
        return cache.computeIfAbsent(clave,
                k -> libro.addPicture(png.get(), Workbook.PICTURE_TYPE_PNG));
    }

    /** Estilos creados UNA vez por libro: POI los acumula por libro. */
    private static final class Estilos {

        private final XSSFWorkbook libro;
        private final Map<Short, XSSFCellStyle> porCuerpo = new HashMap<>();

        Estilos(XSSFWorkbook libro) {
            this.libro = libro;
        }

        XSSFCellStyle negrita(short cuerpo) {
            return porCuerpo.computeIfAbsent(cuerpo, c -> {
                XSSFFont fuente = libro.createFont();
                fuente.setBold(true);
                fuente.setFontHeightInPoints(c);
                XSSFCellStyle estilo = libro.createCellStyle();
                estilo.setFont(fuente);
                estilo.setAlignment(HorizontalAlignment.CENTER);
                estilo.setVerticalAlignment(VerticalAlignment.CENTER);
                return estilo;
            });
        }
    }
}

package com.puntotres.packinglist.service.etiquetas;

/**
 * Coordenadas (0-based de POI) de la hoja de una destinación en la
 * plantilla client-labels/ami-etiquetas-template.xlsx. Cada hoja trae UN
 * par de etiquetas modelo (2 etiquetas idénticas apiladas en vertical =
 * una hoja A4); la caja i-ésima se escribe desplazada i*alturaBloque y la
 * segunda etiqueta del par a +offsetSegundaEtiqueta. Los valores van en la
 * columna C (índice 2) salvo la temporada, en B (índice 1).
 *
 * La plantilla del cliente lleva DOS imágenes flotantes por etiqueta, ancladas
 * en la columna C con los offsets EMU medidos sobre ella:
 * <ul>
 * <li><b>imagenArticulo</b>: la etiqueta de artículo entera (cuatro textos más
 * el EAN-13) compuesta en un solo PNG por ImagenEtiquetaArticulo. Su hueco
 * mide lo mismo en las tres destinaciones, 1674091 × 762000 EMU;</li>
 * <li><b>ean128</b>: el Code 128 de la columna EAN128 del excel de pedido, en
 * el sitio donde antes iba el Code 128 del PO.</li>
 * </ul>
 * El Code 128 del Product Order ya no existe: el cliente lo quitó de su
 * plantilla.
 *
 * filaOrderNumber y filaReferencia apuntan a la fila SUPERIOR de sus celdas
 * combinadas (C8:C9 y C10:C11 en JAPAN y FRANCE, una más abajo en CHINA), que
 * es donde vive el valor de una celda combinada.
 *
 * sufijoPo: sufijo de la columna PO del excel de pedido para esta
 * destinación ("CH", "JP"); null = PO numérico sin sufijo (France).
 *
 * NO cambiar estas coordenadas sin revisar la plantilla, y viceversa:
 * AmiEtiquetaLayoutTest las ancla.
 */
public record AmiEtiquetaLayout(
        String nombreHoja, String sufijoPo, int alturaBloque, int offsetSegundaEtiqueta,
        int filaOrderNumber, int filaTemporada, int filaReferencia, int filaColor,
        int filaTalla, int filaCantidad, int filaPeso, int filaParcel,
        AnclajeBloque imagenArticulo, AnclajeBloque ean128) {

    public static final int COL_TEMPORADA = 1;
    public static final int COL_VALOR = 2;
    public static final int COL_BARCODE = 2;

    public static final AmiEtiquetaLayout CHINA = new AmiEtiquetaLayout(
            "AMI CHINA", "CH", 34, 17,
            8, 11, 10, 12, 13, 14, 15, 16,
            new AnclajeBloque(10, 2481943, 54429, 1674091, 762000),
            new AnclajeBloque(8, 1352897, 143333, 2727960, 452413));

    public static final AmiEtiquetaLayout JAPAN = new AmiEtiquetaLayout(
            "AMI JAPAN", "JP", 32, 16,
            7, 10, 9, 11, 12, 13, 14, 15,
            new AnclajeBloque(9, 2241177, 26896, 1674091, 762000),
            new AnclajeBloque(7, 918884, 62682, 3009900, 502991));

    public static final AmiEtiquetaLayout FRANCE = new AmiEtiquetaLayout(
            "AMI FRANCE", null, 32, 16,
            7, 10, 9, 11, 12, 13, 14, 15,
            new AnclajeBloque(9, 2937163, 69273, 1674091, 762000),
            new AnclajeBloque(7, 2057400, 156331, 2575560, 430408));

    /** Anclaje de la imagen-dirección de JAPAN. */
    public static final AnclajeBloque JAPAN_DIRECCION =
            new AnclajeBloque(2, 66675, 95250, 2562225, 1143000);
}

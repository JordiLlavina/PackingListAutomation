package com.puntotres.packinglist.service.etiquetas;

import java.util.Map;

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
 * alturasFila: altos de fila (en puntos) que hay que corregir sobre los que
 * trae la plantilla, por índice 0-based DENTRO del bloque. Son ajustes del
 * área de impresión que pidió el cliente viendo la etiqueta impresa, así que
 * no se deducen de nada del fichero; viven aquí y no editados a mano en el
 * .xlsx para que se vean en el código, y AmiEtiquetasExcelBuilder los aplica
 * ANTES de capturar el bloque modelo: hecho después solo valdrían para la
 * primera etiqueta del libro. Cada hoja tiene la suya porque las tres
 * maquetaciones no están alineadas entre sí (en CHINA todo va una fila más
 * abajo). En JAPAN y FRANCE la fila corregida es la PRIMERA de la segunda
 * etiqueta del par (= offsetSegundaEtiqueta), que es el hueco entre las dos
 * etiquetas del A4; su homóloga de la primera etiqueta es la fila 0, que es
 * el margen superior de la página y se deja como está.
 *
 * NO cambiar estas coordenadas sin revisar la plantilla, y viceversa:
 * AmiEtiquetaLayoutTest las ancla.
 */
public record AmiEtiquetaLayout(
        String nombreHoja, String nombreHojaPalets, String sufijoPo,
        int alturaBloque, int offsetSegundaEtiqueta,
        int filaOrderNumber, int filaTemporada, int filaReferencia, int filaColor,
        int filaTalla, int filaCantidad, int filaPeso, int filaParcel,
        AnclajeBloque imagenArticulo, AnclajeBloque ean128,
        Map<Integer, Float> alturasFila) {

    public static final int COL_TEMPORADA = 1;
    public static final int COL_VALOR = 2;
    public static final int COL_BARCODE = 2;

    /**
     * La hoja de etiquetas de palet es idéntica en las tres destinaciones
     * (solo cambian los textos fijos del destinatario), así que sus
     * coordenadas son constantes compartidas y no campos del record.
     *
     * El bloque NO arranca en la fila 0: encima lleva una fila con el
     * contador que el cliente apunta a mano. Caben dos etiquetas por A4,
     * media página cada una.
     */
    public static final int FILA_PRIMER_PALET = 1;
    public static final int ALTURA_BLOQUE_PALET = 7;
    public static final int FILA_PALET_COLIS = 5;
    public static final int FILA_PALET_PESO = 6;

    public static final AmiEtiquetaLayout CHINA = new AmiEtiquetaLayout(
            "AMI CHINA", "Etiquetas Palets CHINA", "CH", 34, 17,
            8, 11, 10, 12, 13, 14, 15, 16,
            new AnclajeBloque(10, 2481943, 54429, 1674091, 762000),
            new AnclajeBloque(8, 1352897, 143333, 2727960, 452413),
            Map.of(7, 28.5f));   // fila 8: SUPPLIER CODE

    public static final AmiEtiquetaLayout JAPAN = new AmiEtiquetaLayout(
            "AMI JAPAN", "Etiquetas Palets JAPAN", "JP", 32, 16,
            7, 10, 9, 11, 12, 13, 14, 15,
            new AnclajeBloque(9, 2241177, 26896, 1674091, 762000),
            new AnclajeBloque(7, 918884, 62682, 3009900, 502991),
            Map.of(16, 57f));    // fila 17: hueco entre las dos etiquetas

    public static final AmiEtiquetaLayout FRANCE = new AmiEtiquetaLayout(
            "AMI FRANCE", "Etiquetas Palets FRANCE", null, 32, 16,
            7, 10, 9, 11, 12, 13, 14, 15,
            new AnclajeBloque(9, 2937163, 69273, 1674091, 762000),
            new AnclajeBloque(7, 2057400, 156331, 2575560, 430408),
            Map.of(16, 31f));    // fila 17: hueco entre las dos etiquetas

    /** Anclaje de la imagen-dirección de JAPAN. */
    public static final AnclajeBloque JAPAN_DIRECCION =
            new AnclajeBloque(2, 66675, 95250, 2562225, 1143000);
}

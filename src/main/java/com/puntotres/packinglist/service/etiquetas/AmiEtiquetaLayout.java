package com.puntotres.packinglist.service.etiquetas;

/**
 * Coordenadas (0-based de POI) de la hoja de una destinación en la
 * plantilla client-labels/ami-etiquetas-template.xlsx. Cada hoja trae UN
 * par de etiquetas modelo (2 etiquetas idénticas apiladas en vertical =
 * una hoja A4); la caja i-ésima se escribe desplazada i*alturaBloque y la
 * segunda etiqueta del par a +offsetSegundaEtiqueta. Los valores van en la
 * columna C (índice 2) salvo la temporada, en B (índice 1).
 *
 * Los tres códigos de barras son imágenes flotantes ancladas en la columna C
 * con los offsets EMU medidos en la plantilla de ejemplo: el Code 128 del PO
 * (po), el EAN-13 del artículo (ean13) y el Code 128 de la columna EAN128 del
 * excel de pedido (ean128).
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
        AnclajeBloque po, AnclajeBloque ean13, AnclajeBloque ean128) {

    public static final int COL_TEMPORADA = 1;
    public static final int COL_VALOR = 2;
    public static final int COL_BARCODE = 2;

    public static final AmiEtiquetaLayout CHINA = new AmiEtiquetaLayout(
            "AMI CHINA", "CH", 34, 17,
            9, 11, 11, 12, 13, 14, 15, 16,
            new AnclajeBloque(8, 2971800, 19050, 1047750, 666750),
            new AnclajeBloque(10, 2613660, 162388, 1478280, 652951),
            new AnclajeBloque(12, 1394460, 420424, 2727960, 455876));

    public static final AmiEtiquetaLayout JAPAN = new AmiEtiquetaLayout(
            "AMI JAPAN", "JP", 32, 16,
            8, 10, 10, 11, 12, 13, 14, 15,
            new AnclajeBloque(7, 2857500, 19050, 990600, 628650),
            new AnclajeBloque(9, 2430780, 68580, 1478280, 652951),
            new AnclajeBloque(12, 899160, 15168, 3009900, 502991));

    public static final AmiEtiquetaLayout FRANCE = new AmiEtiquetaLayout(
            "AMI FRANCE", null, 32, 16,
            8, 10, 10, 11, 12, 13, 14, 15,
            new AnclajeBloque(7, 3457575, 9525, 1209675, 762000),
            new AnclajeBloque(9, 3116580, 68580, 1569902, 693420),
            new AnclajeBloque(11, 2095500, 423031, 2575560, 430408));

    /** Anclaje de la imagen-dirección de JAPAN (único PNG de la plantilla). */
    public static final AnclajeBloque JAPAN_DIRECCION =
            new AnclajeBloque(2, 66675, 95250, 2562225, 1143000);
}

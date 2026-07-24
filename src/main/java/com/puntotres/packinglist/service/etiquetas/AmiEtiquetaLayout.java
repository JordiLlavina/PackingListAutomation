package com.puntotres.packinglist.service.etiquetas;

/**
 * Coordenadas (0-based de POI) de la hoja de una destinación en la
 * plantilla client-labels/ami-etiquetas-template.xlsx. Cada hoja trae UN
 * par de etiquetas modelo (2 etiquetas idénticas apiladas en vertical =
 * una hoja A4); la caja i-ésima se escribe desplazada i*alturaBloque y la
 * segunda etiqueta del par a +offsetSegundaEtiqueta. Los valores van en la
 * columna C (índice 2) salvo la temporada, en B (índice 1). El código de
 * barras es una imagen flotante anclada en la columna C con los offsets
 * EMU medidos en la plantilla de ejemplo.
 *
 * sufijoPo: sufijo de la columna PO del excel de pedido para esta
 * destinación ("CH", "JP"); null = PO numérico sin sufijo (France).
 *
 * NO cambiar estas coordenadas sin revisar la plantilla, y viceversa.
 */
public record AmiEtiquetaLayout(
        String nombreHoja, String sufijoPo, int alturaBloque, int offsetSegundaEtiqueta,
        int filaTemporada, int filaReferencia, int filaColor, int filaTalla,
        int filaCantidad, int filaPeso, int filaParcel,
        int filaBarcode, long dxBarcode, long dyBarcode, long cxBarcode, long cyBarcode) {

    public static final int COL_TEMPORADA = 1;
    public static final int COL_VALOR = 2;
    public static final int COL_BARCODE = 2;

    public static final AmiEtiquetaLayout CHINA = new AmiEtiquetaLayout(
            "AMI CHINA", "CH", 34, 17,
            11, 11, 12, 13, 14, 15, 16,
            8, 2971800, 19050, 1047750, 666750);

    public static final AmiEtiquetaLayout JAPAN = new AmiEtiquetaLayout(
            "AMI JAPAN", "JP", 32, 16,
            10, 10, 11, 12, 13, 14, 15,
            7, 2857500, 19050, 990600, 628650);

    public static final AmiEtiquetaLayout FRANCE = new AmiEtiquetaLayout(
            "AMI FRANCE", null, 32, 16,
            10, 10, 11, 12, 13, 14, 15,
            7, 3457575, 9525, 1209675, 762000);

    /** Anclaje de la imagen-dirección de JAPAN (único PNG de la plantilla). */
    public static final int JAPAN_DIRECCION_FILA = 2;
    public static final long JAPAN_DIRECCION_DX = 66675;
    public static final long JAPAN_DIRECCION_DY = 95250;
    public static final long JAPAN_DIRECCION_CX = 2562225;
    public static final long JAPAN_DIRECCION_CY = 1143000;
}

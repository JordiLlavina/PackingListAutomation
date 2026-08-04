package com.puntotres.packinglist.service.etiquetas;

/**
 * Dónde va una imagen dentro del bloque de una etiqueta: la fila relativa al
 * arranque del bloque más el desplazamiento (dx, dy) y el tamaño (cx, cy) en
 * EMU (360 000 EMU = 1 cm), medidos sobre la plantilla real del cliente.
 *
 * La columna no va aquí: las cuatro imágenes de las etiquetas de AMI viven en
 * la columna C (AmiEtiquetaLayout.COL_BARCODE).
 *
 * Lo usan la imagen compuesta del artículo, el Code 128 del EAN128 y la
 * imagen-dirección de Japan.
 */
public record AnclajeBloque(int fila, long dx, long dy, long cx, long cy) {

    /**
     * Proporción ancho/alto del hueco. Las imágenes se encajan a tamaño fijo,
     * así que una que no venga con esta proporción se deforma: quien la genera
     * la usa para renderizarla ya con la forma buena.
     */
    public double proporcion() {
        return (double) cx / cy;
    }
}

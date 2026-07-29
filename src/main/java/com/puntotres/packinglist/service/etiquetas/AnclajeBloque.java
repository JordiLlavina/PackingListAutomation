package com.puntotres.packinglist.service.etiquetas;

/**
 * Dónde va una imagen dentro del bloque de una etiqueta: la fila relativa al
 * arranque del bloque más el desplazamiento (dx, dy) y el tamaño (cx, cy) en
 * EMU (360 000 EMU = 1 cm), medidos sobre la plantilla real del cliente.
 *
 * La columna no va aquí: las cuatro imágenes de las etiquetas de AMI viven en
 * la columna C (AmiEtiquetaLayout.COL_BARCODE).
 *
 * Lo usan los tres códigos de barras de la etiqueta (PO, EAN13, EAN128) y la
 * imagen-dirección de Japan.
 */
public record AnclajeBloque(int fila, long dx, long dy, long cx, long cy) {
}

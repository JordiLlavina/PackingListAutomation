package com.puntotres.packinglist.service.etiquetas;

/**
 * Una fila de la hoja "CODIGOS BARRAS EXTRA": el artículo de una caja que no
 * cabe en su etiqueta y los códigos de barras que le faltan por imprimir.
 *
 * ean13/ean128 null = ese código no se puede dar (artículo no encontrado en
 * el excel de pedido, o columna ausente): la fila se escribe igual, sin esa
 * imagen. El código de barras del PO no está aquí: es de la caja entera y ya
 * va en la etiqueta.
 */
public record FilaCodigoBarrasExtra(int numeroCaja, String referencia, String colorCode,
                                    String talla, String cantidad,
                                    String ean13, String ean128) {
}

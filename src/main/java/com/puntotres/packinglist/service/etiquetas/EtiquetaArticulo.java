package com.puntotres.packinglist.service.etiquetas;

/**
 * Las cinco partes de una etiqueta de artículo, YA formateadas tal como van
 * a la celda o a la imagen ("Size: U", "Cde: 07714", "A236 TRUFFLE").
 *
 * Formatear aquí, en el generador del cliente, y no en el builder, es lo que
 * permite que el builder no sepa nada del cliente ni del excel de pedido.
 *
 * ean13 == null: la fila no traía un EAN13 válido. La etiqueta se imprime
 * igual, sin código de barras, y el generador ya ha dejado su aviso.
 */
public record EtiquetaArticulo(String referencia, String talla, String color,
                               String pedido, String ean13) {
}

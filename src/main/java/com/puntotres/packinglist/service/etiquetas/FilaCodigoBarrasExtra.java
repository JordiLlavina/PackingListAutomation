package com.puntotres.packinglist.service.etiquetas;

/**
 * Un artículo que no cabe en la etiqueta de su caja y va a la hoja
 * "CODIGOS BARRAS EXTRA": los cuatro textos de su etiqueta de artículo, su
 * EAN-13 (dentro de articulo) y su Code 128 largo.
 *
 * parcel es el mismo string que la etiqueta de esa caja ("1 / 15"), para que
 * el operario case la hoja con la caja; destino es el nombre de la
 * destinación del JSON.
 *
 * articulo.ean13() o ean128 null = ese código no se puede dar (artículo no
 * encontrado en el excel de pedido, o columna ausente): el bloque se escribe
 * igual, sin esa imagen. El código de barras del PO no está aquí: el cliente
 * lo quitó de su maqueta.
 */
public record FilaCodigoBarrasExtra(String parcel, String destino,
                                    EtiquetaArticulo articulo, String ean128) {
}

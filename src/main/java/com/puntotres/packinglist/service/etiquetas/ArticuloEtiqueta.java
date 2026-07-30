package com.puntotres.packinglist.service.etiquetas;

/**
 * Un artículo dentro de una caja física: lo que la etiqueta tiene que saber
 * decir de él. Una caja puede llevar varios.
 *
 * talla es null en los bolsos (van como talla única "U" en el excel de
 * pedido) y la talla real en los cinturones, donde cada talla es un SKU
 * distinto con su propio EAN-13.
 */
public record ArticuloEtiqueta(String referencia, String codigoColor, String talla,
                               int cantidad) {
}

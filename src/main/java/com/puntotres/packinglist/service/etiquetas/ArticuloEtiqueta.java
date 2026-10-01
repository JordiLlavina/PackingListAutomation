package com.puntotres.packinglist.service.etiquetas;

/**
 * Un artículo dentro de una caja física: lo que la etiqueta tiene que saber
 * decir de él. Una caja puede llevar varios.
 *
 * talla es null en los bolsos (van como talla única "U" en el excel de
 * pedido) y la talla real en los cinturones, donde cada talla es un SKU
 * distinto con su propio EAN-13.
 *
 * canal es la destinación HIJA del artículo cuando la caja la separa (APC:
 * una caja de WHOLESALE puede llevar material de WHOLESALE y de AUSTRALIA,
 * cada uno con su propio número de pedido) y null cuando no la separa.
 */
public record ArticuloEtiqueta(String referencia, String codigoColor, String talla,
                               int cantidad, String canal) {

    /** Un artículo sin destinación hija: la caja no separa por canal. */
    public ArticuloEtiqueta(String referencia, String codigoColor, String talla, int cantidad) {
        this(referencia, codigoColor, talla, cantidad, null);
    }
}

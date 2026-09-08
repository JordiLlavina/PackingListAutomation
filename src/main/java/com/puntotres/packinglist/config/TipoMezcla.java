package com.puntotres.packinglist.config;

/**
 * Qué se puede meter junto en una misma caja, según la norma del cliente para
 * esa destinación.
 *
 * Ninguna de las tres permite mezclar cartones distintos ni juntar un bolso
 * con un cinturón: la caja ES un cartón de una medida concreta y los dos
 * tipos de artículo se preparan por separado, y eso no lo cambia ninguna
 * norma comercial.
 */
public enum TipoMezcla {

    /**
     * Una sola referencia y un solo color por caja. Las TALLAS de ese color sí
     * comparten caja: cada una es un artículo con su propio código de barras,
     * pero separarlas dejaría media caja vacía por talla.
     */
    NINGUNA,

    /** Solo se mezcla entre artículos del mismo número de pedido. */
    MISMO_PEDIDO,

    /** Se mezclan modelos y colores, agotando antes los colores de una referencia. */
    LIBRE
}

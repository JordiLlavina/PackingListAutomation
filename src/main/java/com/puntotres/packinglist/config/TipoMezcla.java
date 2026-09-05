package com.puntotres.packinglist.config;

/**
 * Qué se puede meter junto en una misma caja, según la norma del cliente para
 * esa destinación.
 *
 * Ninguna de las tres permite mezclar cartones distintos: la caja ES un
 * cartón de una medida concreta, y eso no lo cambia ninguna norma comercial.
 */
public enum TipoMezcla {

    /** Una sola referencia, un solo color y una sola talla por caja. */
    NINGUNA,

    /** Solo se mezcla entre artículos del mismo número de pedido. */
    MISMO_PEDIDO,

    /** Se mezclan modelos y colores, agotando antes los colores de una referencia. */
    LIBRE
}

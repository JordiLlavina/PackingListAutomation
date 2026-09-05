package com.puntotres.packinglist.service.taller;

/**
 * De dónde ha salido el cartón y las unidades por caja de una referencia.
 *
 * Se enseña en la pantalla de ajuste porque cambia cuánto hay que fiarse:
 * lo que viene de memoria lo decidió una persona en un envío anterior, lo del
 * taller es una sugerencia de quien ha fabricado, y el valor por defecto no lo
 * ha mirado nadie.
 */
public enum OrigenDato {

    /** Lo que se usó la última vez con esta referencia. */
    MEMORIA,

    /** Lo que sugiere la hoja del taller. */
    TALLER,

    /** Nadie lo ha dicho: cartón estándar y unidades por caja sin rellenar. */
    POR_DEFECTO
}

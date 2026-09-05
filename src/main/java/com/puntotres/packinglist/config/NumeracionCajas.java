package com.puntotres.packinglist.config;

/**
 * Cómo se numeran las cajas de un envío con varias destinaciones. Es una
 * costumbre de cada cliente, no una decisión del programa: el número acaba
 * pegado al bulto y tiene que cuadrar con lo que el cliente espera recibir.
 */
public enum NumeracionCajas {

    /** El contador sigue de una destinación a la siguiente (AMI). */
    CONTINUA,

    /** Cada destinación empieza otra vez por la caja 1 (APC y el resto). */
    POR_DESTINACION
}

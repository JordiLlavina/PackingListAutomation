package com.puntotres.packinglist.modelos.api.dto;

/**
 * Los números de la bandeja de quien la mira: lo que le ha llegado y lo que
 * tiene por hacer, según su lado (ver {@code Modificaciones.resumen}).
 *
 * @param urgentes órdenes para el taller abiertas y marcadas urgentes (los dos lados)
 */
public record ResumenBandeja(int nuevas, int pendientes, int urgentes, int conRespuesta) {
}

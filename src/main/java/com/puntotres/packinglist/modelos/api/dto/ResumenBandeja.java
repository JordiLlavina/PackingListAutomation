package com.puntotres.packinglist.modelos.api.dto;

/**
 * Los números de la bandeja de quien la mira: lo que le ha llegado y lo que
 * tiene por hacer, según su lado (ver {@code Modificaciones.resumen}).
 *
 * @param sinPrioridad solo en el despacho: avisos del taller abiertos y sin prioridad
 */
public record ResumenBandeja(int nuevas, int pendientes, int vencidas, int conRespuesta, int sinPrioridad) {
}

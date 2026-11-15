package com.puntotres.packinglist.modelos.persistence;

/**
 * Dónde aplica el despacho un aviso del taller. Los que se ofrecen salen de
 * {@code modelos.destinos-aplicacion} del yml; estas son las claves que se
 * saben guardar.
 *
 * <p>{@link #PEDIR_TROQUELES} no es un sitio donde se apunta el cambio, es
 * una tarea que solo hace falta cuando el cambio toca los patrones o los
 * troqueles: por eso nace como «no aplica» en los demás avisos
 * ({@code Modificaciones.aplicaDeEntrada}).
 */
public enum DestinoAplicacion {
    ICSUITE, EXCEL_ESCANDALLOS, PEDIR_TROQUELES
}

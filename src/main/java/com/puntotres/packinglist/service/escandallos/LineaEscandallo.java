package com.puntotres.packinglist.service.escandallos;

/**
 * Una línea de la tabla de materiales de un escandallo del ERP.
 *
 * {@code cantidad} es {@code Double} y no {@code double} a propósito, igual
 * que los pesos del packing list: {@code null} significa «no se ha podido
 * leer» y deja la celda en blanco en el excel de salida en vez de escribir un
 * cero que parecería un dato real.
 */
public record LineaEscandallo(String article, String descripcion, Double cantidad) {
}

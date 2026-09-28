package com.puntotres.packinglist.service.corte;

/**
 * Una fila del excel de pedido tal como la necesita el corte: referencia
 * completa, color y unidades. La talla, la destinación y el número de pedido
 * van en filas distintas y aquí dan igual: se suman.
 */
public record LineaCorte(String referencia, String color, int cantidad) {
}

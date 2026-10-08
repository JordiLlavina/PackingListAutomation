package com.puntotres.packinglist.service.corte;

/**
 * Una ficha del Excel de órdenes de corte: una referencia (modelo + piel) en
 * un color. Se titula con el {@code modelo} a secas, que es como la quiere el
 * cortador; la piel ya la dicen los nombres de debajo. La {@code referencia}
 * entera ("ULL027.AL0103", "PXCBC-F67008") es la que nombra su hoja: el mismo
 * modelo en el mismo color va a menudo en dos pieles. {@code color} es el
 * color del bolso tal como se enseña, código y descripción ("LZZ NOIR"), y
 * las pieles llevan el suyo de ese color. {@code fotoPrincipal} es null
 * cuando el modelo no ha traído fotos legibles.
 */
public record OrdenCorte(String cliente, String temporada, String referencia, String modelo,
                         String color, int bolsos, PielesArticulo pieles, Imagen fotoPrincipal) {
}

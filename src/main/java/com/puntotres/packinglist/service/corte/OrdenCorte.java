package com.puntotres.packinglist.service.corte;

/**
 * Una página del Word de órdenes de corte: una referencia (modelo + piel) en
 * un color. Se titula con el {@code modelo} a secas, que es como la quiere el
 * cortador; la piel ya la dicen los nombres de debajo. {@code fotoPrincipal}
 * es null cuando el modelo no ha traído fotos legibles.
 */
public record OrdenCorte(String cliente, String temporada, String modelo, String color,
                         int bolsos, PielesArticulo pieles, Imagen fotoPrincipal) {
}

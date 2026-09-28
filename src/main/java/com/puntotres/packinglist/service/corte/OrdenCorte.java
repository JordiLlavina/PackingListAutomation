package com.puntotres.packinglist.service.corte;

/**
 * Una página del Word de órdenes de corte: un modelo + piel en un color.
 * {@code fotoPrincipal} es null cuando el modelo no ha traído fotos legibles.
 */
public record OrdenCorte(String cliente, String temporada, String referencia, String color,
                         int bolsos, PielesArticulo pieles, Imagen fotoPrincipal) {
}

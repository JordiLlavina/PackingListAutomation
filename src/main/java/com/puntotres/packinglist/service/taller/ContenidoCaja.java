package com.puntotres.packinglist.service.taller;

/** Lo que hay de un artículo dentro de una caja concreta. */
public record ContenidoCaja(
        String referencia,
        String color,
        String talla,
        String pedido,
        int unidades) {
}

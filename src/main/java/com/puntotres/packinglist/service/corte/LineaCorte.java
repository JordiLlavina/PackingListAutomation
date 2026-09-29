package com.puntotres.packinglist.service.corte;

/**
 * Una fila del excel de pedido tal como la necesita el corte: referencia
 * completa, color, unidades y el nombre del bolso si el pedido lo trae (APC
 * en su "Désignation"; AMI no lo trae). La talla, la destinación y el número
 * de pedido van en filas distintas y aquí dan igual: se suman.
 */
public record LineaCorte(String referencia, String color, int cantidad, String nombreModelo) {

    public LineaCorte {
        nombreModelo = nombreModelo == null ? "" : nombreModelo.trim();
    }

    public LineaCorte(String referencia, String color, int cantidad) {
        this(referencia, color, cantidad, "");
    }
}

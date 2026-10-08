package com.puntotres.packinglist.service.corte;

/**
 * Una fila del excel de pedido tal como la necesita el corte: referencia
 * completa, color (código y, si el pedido la trae, descripción), unidades y
 * el nombre del bolso si el pedido lo trae (APC en su "Désignation"; AMI no
 * lo trae). La talla, la destinación y el número de pedido van en filas
 * distintas y aquí dan igual: se suman.
 */
public record LineaCorte(String referencia, String codigoColor, String descripcionColor,
                         int cantidad, String nombreModelo) {

    public LineaCorte {
        codigoColor = codigoColor == null ? "" : codigoColor.trim();
        descripcionColor = descripcionColor == null ? "" : descripcionColor.trim();
        nombreModelo = nombreModelo == null ? "" : nombreModelo.trim();
    }

    /** Un color sin descripción en el pedido: la de APC, que solo trae el código. */
    public LineaCorte(String referencia, String codigoColor, int cantidad, String nombreModelo) {
        this(referencia, codigoColor, "", cantidad, nombreModelo);
    }

    public LineaCorte(String referencia, String codigoColor, int cantidad) {
        this(referencia, codigoColor, "", cantidad, "");
    }
}

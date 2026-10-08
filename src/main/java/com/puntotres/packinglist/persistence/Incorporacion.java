package com.puntotres.packinglist.persistence;

import java.util.List;

/**
 * Lo que ha pasado al meter en una tabla (colores o pieles) lo que trae un
 * excel de pedido: los códigos añadidos, los que ya estaban sin descripción y
 * la han cogido del pedido, y los que ya estaban y no se han tocado.
 */
public record Incorporacion(List<String> anadidos, List<String> completados, List<String> yaEstaban) {

    public Incorporacion {
        anadidos = List.copyOf(anadidos);
        completados = List.copyOf(completados);
        yaEstaban = List.copyOf(yaEstaban);
    }

    public int total() {
        return anadidos.size() + completados.size() + yaEstaban.size();
    }
}

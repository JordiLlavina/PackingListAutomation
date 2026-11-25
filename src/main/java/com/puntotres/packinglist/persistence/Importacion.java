package com.puntotres.packinglist.persistence;

import java.util.List;

/**
 * Lo que ha pasado al importar en una tabla (colores o pieles) el excel que
 * se exporta desde la pantalla Colores y Pieles de Cliente: los códigos
 * añadidos, los que han cambiado de texto y los que se han quedado como
 * estaban (iguales, o con la casilla vacía, que no borra).
 */
public record Importacion(List<String> anadidos, List<String> cambiados, List<String> sinCambios) {

    public Importacion {
        anadidos = List.copyOf(anadidos);
        cambiados = List.copyOf(cambiados);
        sinCambios = List.copyOf(sinCambios);
    }

    public int total() {
        return anadidos.size() + cambiados.size() + sinCambios.size();
    }
}

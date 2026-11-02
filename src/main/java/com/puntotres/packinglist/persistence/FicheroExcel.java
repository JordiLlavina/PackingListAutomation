package com.puntotres.packinglist.persistence;

/** Un excel subido: su nombre (el que se enseña y se descarga) y su contenido. */
public record FicheroExcel(String nombre, byte[] bytes) {

    public FicheroExcel {
        if (bytes == null) {
            throw new IllegalArgumentException("Un fichero sin contenido no es un fichero");
        }
    }
}

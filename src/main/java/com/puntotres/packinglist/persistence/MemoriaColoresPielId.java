package com.puntotres.packinglist.persistence;

import java.io.Serializable;

/** Clave de los colores de piel: cliente + referencia + código de color del bolso. */
public record MemoriaColoresPielId(String cliente, String referencia, String codigoColor)
        implements Serializable {

    /** JPA exige un constructor sin argumentos para la clase de la clave. */
    public MemoriaColoresPielId() {
        this(null, null, null);
    }
}

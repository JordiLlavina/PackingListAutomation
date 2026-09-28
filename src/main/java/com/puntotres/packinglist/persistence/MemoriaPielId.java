package com.puntotres.packinglist.persistence;

import java.io.Serializable;

/** Clave del nombre de una piel: cliente + referencia de la piel ("AMI" + "AL0103"). */
public record MemoriaPielId(String cliente, String refPiel) implements Serializable {

    /** JPA exige un constructor sin argumentos para la clase de la clave. */
    public MemoriaPielId() {
        this(null, null);
    }
}

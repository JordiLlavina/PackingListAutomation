package com.puntotres.packinglist.persistence;

import java.io.Serializable;

/** Clave del forro y las combinaciones: cliente + referencia (modelo + piel, "ULL027.AL0103"). */
public record MemoriaPielesArticuloId(String cliente, String referencia) implements Serializable {

    /** JPA exige un constructor sin argumentos para la clase de la clave. */
    public MemoriaPielesArticuloId() {
        this(null, null);
    }
}

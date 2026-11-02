package com.puntotres.packinglist.persistence;

import java.io.Serializable;

/** Clave de un artículo: cliente + referencia entera ("AMI" + "ULL027.AL0103"). */
public record ArticuloId(String cliente, String referencia) implements Serializable {

    /** JPA exige un constructor sin argumentos para la clase de la clave. */
    public ArticuloId() {
        this(null, null);
    }
}

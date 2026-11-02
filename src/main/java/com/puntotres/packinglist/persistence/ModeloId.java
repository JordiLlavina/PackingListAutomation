package com.puntotres.packinglist.persistence;

import java.io.Serializable;

/** Clave de un modelo: cliente + código del modelo ("AMI" + "ULL027"). */
public record ModeloId(String cliente, String codigo) implements Serializable {

    /** JPA exige un constructor sin argumentos para la clase de la clave. */
    public ModeloId() {
        this(null, null);
    }
}

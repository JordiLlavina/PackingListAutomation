package com.puntotres.packinglist.persistence;

import java.io.Serializable;

/** Clave de un color de la Tabla colores Clientes: cliente + código ("APC" + "LZZ"). */
public record ColorClienteId(String cliente, String codigo) implements Serializable {

    /** JPA exige un constructor sin argumentos para la clase de la clave. */
    public ColorClienteId() {
        this(null, null);
    }
}

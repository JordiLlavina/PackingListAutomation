package com.puntotres.packinglist.persistence;

import java.io.Serializable;

/** Clave del color de un artículo: cliente + referencia + código del color del bolso (o su descripción si no tiene código). */
public record ArticuloColorId(String cliente, String referencia, String codigoColor) implements Serializable {

    /** JPA exige un constructor sin argumentos para la clase de la clave. */
    public ArticuloColorId() {
        this(null, null, null);
    }
}

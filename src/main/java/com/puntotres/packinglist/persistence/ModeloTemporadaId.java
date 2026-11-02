package com.puntotres.packinglist.persistence;

import java.io.Serializable;

/** Clave del vínculo modelo ↔ temporada. */
public record ModeloTemporadaId(String cliente, String codigoModelo, Long temporadaId) implements Serializable {

    /** JPA exige un constructor sin argumentos para la clase de la clave. */
    public ModeloTemporadaId() {
        this(null, null, null);
    }
}

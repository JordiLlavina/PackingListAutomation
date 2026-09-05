package com.puntotres.packinglist.persistence;

import java.io.Serializable;

/**
 * Clave de la memoria de referencias: cliente + referencia.
 *
 * El color NO forma parte de la clave a propósito: todos los colores de una
 * referencia se empaquetan en el mismo cartón y con las mismas unidades por
 * caja, así que memorizar por color solo multiplicaría las filas.
 */
public record MemoriaCajaReferenciaId(String cliente, String referencia) implements Serializable {

    /** JPA exige un constructor sin argumentos para la clase de la clave. */
    public MemoriaCajaReferenciaId() {
        this(null, null);
    }
}

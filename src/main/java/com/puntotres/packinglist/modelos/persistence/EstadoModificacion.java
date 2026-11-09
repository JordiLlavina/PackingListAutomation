package com.puntotres.packinglist.modelos.persistence;

/**
 * Por dónde va una modificación. Nace {@code NUEVA}, pasa a {@code VISTA} al
 * abrirla quien la recibe, a {@code EN_CURSO} cuando el despacho marca el
 * primer destino aplicado y acaba {@code HECHA} o {@code DESCARTADA}. Ninguna
 * se borra: una descartada conserva todo.
 */
public enum EstadoModificacion {
    NUEVA, VISTA, EN_CURSO, HECHA, DESCARTADA;

    /** HECHA o DESCARTADA: ya no queda nada que hacer. */
    public boolean cerrada() {
        return this == HECHA || this == DESCARTADA;
    }
}

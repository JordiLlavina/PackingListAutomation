package com.puntotres.packinglist.persistence;

/** Qué pasó con una piel y un cliente en una fecha: los pasos de su recorrido. */
public enum TipoEventoPiel {

    BUSCADA("Buscada"),
    MOSTRADA("Mostrada"),
    MUESTRA("Muestra enviada"),
    PROTOTIPO("Prototipo"),
    SMS("SMS"),
    PRODUCCION("Producción"),
    DESCARTADA("Descartada");

    private final String texto;

    TipoEventoPiel(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }
}

package com.puntotres.packinglist.persistence;

/**
 * En qué punto de su vida está un modelo. Un prototipo todavía no tiene
 * código del cliente (se conoce por su nombre de prototipo); las muestras de
 * venta (SMS) ya suelen tenerlo; en producción es un modelo del pedido; e
 * inactivo, uno que ya no se fabrica pero cuyo historial se conserva.
 *
 * Los modelos que ya estaban en el catálogo antes de esta columna, y los que
 * da de alta el excel de una temporada, son de PRODUCCION. La etapa la
 * cambia una persona, nunca el alta automática.
 */
public enum Etapa {
    PROTOTIPO("Prototipo"),
    SMS("Muestras"),
    PRODUCCION("Producción"),
    INACTIVO("Inactivo");

    private final String texto;

    Etapa(String texto) {
        this.texto = texto;
    }

    /** Lo que se enseña en pantalla. */
    public String texto() {
        return texto;
    }
}

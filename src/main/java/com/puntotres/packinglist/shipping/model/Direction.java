package com.puntotres.packinglist.shipping.model;

/**
 * Sentido respecto a España, donde está la sede. CROSS_TRADE es un envío que
 * ni sale ni entra en España (Marruecos → Francia); DOMESTIC, dentro de España.
 */
public enum Direction {
    EXPORT("Exportación"), IMPORT("Importación"), DOMESTIC("Nacional"), CROSS_TRADE("Entre terceros países");

    private final String etiqueta;
    Direction(String etiqueta) { this.etiqueta = etiqueta; }
    public String getEtiqueta() { return etiqueta; }
}

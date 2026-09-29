package com.puntotres.packinglist.service.corte;

import java.util.concurrent.Executors;

/**
 * Conversiones de fotos a medida para los tests de la capa web, que no
 * pueden construirlas (el constructor es del paquete) ni esperar a que una
 * conversión de verdad se quede a medias.
 */
public final class ConversionesDePrueba {

    private ConversionesDePrueba() {
    }

    /** Una conversión que no acaba nunca por sí sola: solo si se cancela. */
    public static ConversionFotos sinTerminar(int total) {
        return new ConversionFotos(total, Executors.newSingleThreadExecutor());
    }
}

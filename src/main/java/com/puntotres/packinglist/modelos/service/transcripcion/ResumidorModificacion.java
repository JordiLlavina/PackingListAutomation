package com.puntotres.packinglist.modelos.service.transcripcion;

import java.io.IOException;

/**
 * Resume para el despacho la transcripción de una nota de voz. El de verdad es
 * {@link ResumidorClaude}; sin clave de la API de Anthropic se usa
 * {@link ResumidorDesactivado} (lo elige {@link TranscripcionConfig}).
 */
public interface ResumidorModificacion {

    /** Si se puede resumir; si no, la nota de voz se queda solo con su transcripción. */
    boolean disponible();

    /**
     * @param transcripcion lo que se ha dicho, nunca vacío
     * @param tipoCambio    el tipo de cambio que eligió quien la grabó (su texto del yml), o vacío
     * @param modelo        el modelo, como se nombra en pantalla ({@code ULL027 · Bolso Candy})
     * @throws IOException con un mensaje que se pueda enseñar si el servicio falla
     */
    ResumenIa resumir(String transcripcion, String tipoCambio, String modelo) throws IOException;
}

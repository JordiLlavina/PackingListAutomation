package com.puntotres.packinglist.modelos.service.transcripcion;

import java.io.IOException;

/** El resumidor cuando no hay {@code ANTHROPIC_API_KEY}: la nota de voz se queda solo con su transcripción. */
public class ResumidorDesactivado implements ResumidorModificacion {

    @Override
    public boolean disponible() {
        return false;
    }

    /** No debería llamarse nunca: quien resume pregunta antes por {@link #disponible()}. */
    @Override
    public ResumenIa resumir(String transcripcion, String tipoCambio, String modelo) throws IOException {
        throw new IOException("El resumen de la IA no está configurado (falta ANTHROPIC_API_KEY)");
    }
}

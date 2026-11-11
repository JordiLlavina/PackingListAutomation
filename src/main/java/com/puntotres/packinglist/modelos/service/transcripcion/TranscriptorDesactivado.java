package com.puntotres.packinglist.modelos.service.transcripcion;

import java.io.IOException;

/**
 * El transcriptor cuando no hay clave o región de Azure Speech: no está
 * disponible y las notas de voz se quedan en {@code NO_APLICA} («sin
 * transcripción, servicio no configurado»), que no es un fallo.
 */
public class TranscriptorDesactivado implements Transcriptor {

    @Override
    public boolean disponible() {
        return false;
    }

    /** No debería llamarse nunca: quien transcribe pregunta antes por {@link #disponible()}. */
    @Override
    public String transcribir(byte[] audio, String mime) throws IOException {
        throw new IOException("La transcripción no está configurada (faltan AZURE_SPEECH_KEY y su región)");
    }
}

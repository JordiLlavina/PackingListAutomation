package com.puntotres.packinglist.modelos.service.transcripcion;

import java.io.IOException;

/**
 * Pasa una nota de voz a texto. El de verdad es {@link AzureSpeechTranscriptor};
 * sin credenciales de Azure se usa {@link TranscriptorDesactivado}, que dice
 * que no está disponible (lo elige {@link TranscripcionConfig}).
 */
public interface Transcriptor {

    /** Si se puede transcribir; si no, la nota de voz se queda sin transcripción, sin que eso sea un fallo. */
    boolean disponible();

    /**
     * El texto de la nota de voz, vacío si no se ha dicho nada (un silencio).
     *
     * @param mime el tipo tal como lo dijo el navegador ({@code audio/webm;codecs=opus})
     * @throws IOException con un mensaje que se pueda enseñar si el servicio falla
     */
    String transcribir(byte[] audio, String mime) throws IOException;
}

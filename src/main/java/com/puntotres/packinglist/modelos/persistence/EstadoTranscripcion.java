package com.puntotres.packinglist.modelos.persistence;

/**
 * Por dónde va la transcripción de un adjunto. Una nota de voz nace
 * {@code PENDIENTE} y acaba {@code HECHA} o {@code FALLIDA} (con su error);
 * una foto o un documento no se transcriben: {@code NO_APLICA}.
 */
public enum EstadoTranscripcion {
    PENDIENTE, HECHA, FALLIDA, NO_APLICA
}

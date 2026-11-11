package com.puntotres.packinglist.modelos.service;

/**
 * Se acaba de guardar una nota de voz y queda pendiente de transcribir. Lo
 * publica {@link Adjuntos#guardar}; quien transcribe lo escucha cuando la
 * transacción se confirma, para no leer un adjunto que todavía no existe.
 */
public record AdjuntoAudioGuardado(Long adjuntoId) {
}

package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDateTime;

/**
 * Un adjunto SIN sus bytes: lo que hace falta para pintar un mensaje, contar
 * fotos y notas de voz o buscar en las transcripciones. Va aparte de la
 * entidad por lo mismo que {@code ResumenFoto}: listar no puede traerse las
 * notas de voz enteras.
 */
public record ResumenAdjunto(Long id, Long mensajeId, TipoAdjunto tipo, String nombre, String mime, long tamano,
                             Integer duracionSeg, String transcripcion, String resumenIa,
                             EstadoTranscripcion estadoTranscripcion, String error, LocalDateTime fecha) {
}

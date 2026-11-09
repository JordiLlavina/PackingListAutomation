package com.puntotres.packinglist.modelos.api.dto;

import com.puntotres.packinglist.modelos.persistence.EstadoTranscripcion;
import com.puntotres.packinglist.modelos.persistence.ResumenAdjunto;
import com.puntotres.packinglist.modelos.persistence.TipoAdjunto;

/**
 * Un adjunto de un mensaje, sin sus bytes: se descargan aparte por su id.
 *
 * @param tamano      bytes de lo guardado (una foto, ya reducida)
 * @param duracionSeg solo en las notas de voz
 * @param error       por qué falló la transcripción; null si no falló
 */
public record AdjuntoVista(Long id, TipoAdjunto tipo, String nombre, String mime, long tamano, Integer duracionSeg,
                           String transcripcion, String resumenIa, EstadoTranscripcion estadoTranscripcion,
                           String error) {

    public static AdjuntoVista de(ResumenAdjunto adjunto) {
        return new AdjuntoVista(adjunto.id(), adjunto.tipo(), adjunto.nombre(), adjunto.mime(), adjunto.tamano(),
                adjunto.duracionSeg(), adjunto.transcripcion(), adjunto.resumenIa(), adjunto.estadoTranscripcion(),
                adjunto.error());
    }
}

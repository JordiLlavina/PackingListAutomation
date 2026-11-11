package com.puntotres.packinglist.modelos.service;

import com.puntotres.packinglist.modelos.persistence.TipoAdjunto;

/**
 * Un adjunto tal como llega, sin validar ni normalizar todavía: eso lo hace
 * {@link Adjuntos#guardar}.
 *
 * @param nombre      el nombre original del fichero
 * @param mime        el tipo que dice el navegador
 * @param duracionSeg los segundos de una nota de voz; null en lo demás
 */
public record AdjuntoNuevo(TipoAdjunto tipo, String nombre, String mime, byte[] bytes, Integer duracionSeg) {
}

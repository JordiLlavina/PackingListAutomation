package com.puntotres.packinglist.modelos.api.dto;

import java.time.LocalDateTime;

/**
 * Una entrada de la línea de tiempo de un modelo: un cambio de etapa, un
 * nombre nuevo o una modificación.
 *
 * @param tipo            {@code MODIFICACION}, {@code ETAPA} o {@code CODIGO}
 * @param quien           el nombre de quien lo hizo, o «—» si no lo hizo nadie en concreto
 * @param modificacionId  la modificación de la que habla; null si no es una
 * @param audioAdjuntoId  la nota de voz que la acompaña; null si no hay
 * @param estado          el estado de la modificación hoy ({@code NUEVA}, {@code VISTA},
 *                        {@code EN_CURSO}, {@code HECHA} o {@code DESCARTADA}), que la
 *                        ficha pinta en el punto; null si el evento no es una modificación
 */
public record Evento(LocalDateTime fecha, String tipo, String texto, String quien, Long modificacionId,
                     Long audioAdjuntoId, String estado) {
}

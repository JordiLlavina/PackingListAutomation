package com.puntotres.packinglist.modelos.service;

/**
 * El taller acaba de marcar hecha una orden del despacho. Lo publica
 * {@link Modificaciones#marcarHecha}; {@link Push} lo escucha cuando la
 * transacción se confirma y avisa al despacho, que es quien tiene que
 * trasladar el cambio a ICSUITE y al escandallo. Una orden que cierra el
 * propio despacho no lo publica: ya lo sabe.
 *
 * @param hechaPorId quién la marcó; null si no fue una persona
 */
public record OrdenHecha(Long id, Long hechaPorId) {
}

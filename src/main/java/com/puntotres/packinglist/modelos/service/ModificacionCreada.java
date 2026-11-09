package com.puntotres.packinglist.modelos.service;

import com.puntotres.packinglist.modelos.persistence.Direccion;

/**
 * Se acaba de crear un aviso del taller o una orden del despacho. Lo publica
 * {@link Modificaciones}; {@link Push} lo escucha cuando la transacción se
 * confirma y avisa al otro lado.
 */
public record ModificacionCreada(Long id, Direccion direccion, Long creadaPorId) {
}

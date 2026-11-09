package com.puntotres.packinglist.modelos.service;

import com.puntotres.packinglist.modelos.persistence.Rol;

/**
 * Alguien acaba de contestar en una modificación. Lo publica
 * {@link Modificaciones#responder}; {@link Push} lo escucha cuando la
 * transacción se confirma y avisa al otro lado, esté como esté la
 * modificación (también si ya está cerrada).
 *
 * @param autor el rol de quien ha contestado (un ADMIN habla por el despacho)
 */
public record MensajeRespondido(Long modificacionId, Rol autor) {
}

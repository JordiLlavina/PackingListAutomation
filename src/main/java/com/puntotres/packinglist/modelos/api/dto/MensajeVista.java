package com.puntotres.packinglist.modelos.api.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.puntotres.packinglist.modelos.persistence.Rol;

/**
 * Un mensaje de una modificación con sus adjuntos.
 *
 * @param autor    el nombre de quien lo escribió; null si no lo escribió una persona
 * @param rolAutor su rol, que dice de qué lado habla; null si no hay autor
 * @param texto    vacío cuando todo lo que dice va en los adjuntos
 */
public record MensajeVista(Long id, String autor, Rol rolAutor, String texto, LocalDateTime creadoEn,
                           List<AdjuntoVista> adjuntos) {
}

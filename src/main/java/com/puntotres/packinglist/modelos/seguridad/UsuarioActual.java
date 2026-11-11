package com.puntotres.packinglist.modelos.seguridad;

import java.io.Serializable;

import com.puntotres.packinglist.modelos.persistence.Rol;

/**
 * Quién hace la petición en la app de modelos: lo que viaja en la sesión (o
 * se monta en cada petición con la cookie del dispositivo). Es una foto del
 * usuario al entrar, no la entidad: así la sesión no arrastra nada de JPA.
 *
 * @param dispositivoId el aparato con el que ha entrado; null si ha entrado
 *                      con contraseña
 */
public record UsuarioActual(Long id, String nombre, Rol rol, Long dispositivoId) implements Serializable {

    /** DESPACHO o ADMIN. */
    public boolean esDespacho() {
        return rol != null && rol.esDespacho();
    }
}

package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;

/**
 * Tiene que quedar al menos un administrador activo: si no, nadie podría dar
 * de alta usuarios ni generar códigos para la tablet. La propiedad de abajo no
 * configura nada: solo da a esta clase su propio contexto y, con él, su propia
 * base en memoria (con nombre aleatorio), donde se sabe cuántos admins hay;
 * en la base compartida de la suite otros tests crean los suyos.
 */
@SpringBootTest(properties = "prueba.contexto-propio=ultimo-administrador")
class UltimoAdministradorTest {

    @Autowired
    private Usuarios usuarios;

    @Test
    void elUltimoAdministradorActivoNoSeDesactivaNiPierdeElRol() {
        Usuario unico = usuarios.crear(new Usuarios.Alta("Único admin", "t27-unico-admin", "clave", Rol.ADMIN));

        IllegalArgumentException desactivar = assertThrows(IllegalArgumentException.class,
                () -> usuarios.cambiar(unico.getId(), null, null, false, null));
        assertEquals("Tiene que quedar al menos un administrador activo", desactivar.getMessage());
        IllegalArgumentException degradar = assertThrows(IllegalArgumentException.class,
                () -> usuarios.cambiar(unico.getId(), null, Rol.DESPACHO, null, null));
        assertEquals("Tiene que quedar al menos un administrador activo", degradar.getMessage());
        assertTrue(usuarios.porId(unico.getId()).orElseThrow().isActivo());

        // Con otro administrador activo, sí.
        usuarios.crear(new Usuarios.Alta("Segundo admin", "t27-segundo-admin", "clave", Rol.ADMIN));
        usuarios.cambiar(unico.getId(), null, null, false, null);
        assertFalse(usuarios.porId(unico.getId()).orElseThrow().isActivo());
    }
}

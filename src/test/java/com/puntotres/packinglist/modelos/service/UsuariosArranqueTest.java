package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.persistence.UsuarioRepository;

/**
 * El primer arranque: sin usuarios y con contraseña de administración se crea
 * el {@code admin}. Va en su propia clase porque necesita su propio contexto:
 * con otras propiedades Spring levanta otro, y la base en memoria lleva otro
 * nombre aleatorio (ver application-test.yml), así que arranca con la tabla
 * de usuarios vacía.
 */
@SpringBootTest(properties = "modelos.admin-password=prueba")
class UsuariosArranqueTest {

    @Autowired
    private UsuarioRepository repositorio;

    @Autowired
    private Usuarios usuarios;

    @Test
    void sinUsuariosYConContrasenaDeAdminSeCreaAdmin() {
        Usuario admin = repositorio.findAll().stream()
                .filter(usuario -> "admin".equals(usuario.getLogin()))
                .findFirst().orElseThrow();

        assertEquals("Administrador", admin.getNombre());
        assertEquals(Rol.ADMIN, admin.getRol());
        assertTrue(usuarios.autenticar("admin", "prueba").isPresent());
    }
}

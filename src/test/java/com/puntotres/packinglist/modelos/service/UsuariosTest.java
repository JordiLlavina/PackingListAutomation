package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.persistence.UsuarioRepository;

/**
 * Los usuarios de la app de modelos. Cada test usa sus propios logins
 * ({@code t5-...}): la base de datos en memoria es la misma para toda la
 * suite.
 */
@SpringBootTest
class UsuariosTest {

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private UsuarioRepository repositorio;

    @Test
    void crearUsuarioGuardaElHashYNoLaContrasena() {
        Usuario creado = usuarios.crear(new Usuarios.Alta("Pepa", "t5-pepa", "secreta", Rol.DESPACHO));

        Usuario guardado = repositorio.findById(creado.getId()).orElseThrow();
        assertNotEquals("secreta", guardado.getPasswordHash());
        assertTrue(new BCryptPasswordEncoder().matches("secreta", guardado.getPasswordHash()),
                "lo guardado es el hash BCrypt de la contraseña");
        assertEquals(Rol.DESPACHO, guardado.getRol());
        assertTrue(guardado.isActivo());
        assertNotNull(guardado.getFechaAlta());
    }

    @Test
    void unLoginRepetidoSeRechazaSinMirarMayusculas() {
        usuarios.crear(new Usuarios.Alta("Luis", "t5-luis", "uno", Rol.TALLER));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> usuarios.crear(new Usuarios.Alta("Otro Luis", "T5-LUIS", "dos", Rol.TALLER)));
        assertTrue(error.getMessage().startsWith("Ya hay un usuario con el login"), error.getMessage());
    }

    @Test
    void autenticarRechazaInactivosYContrasenaMala() {
        Usuario ana = usuarios.crear(new Usuarios.Alta("Ana", "t5-ana", "buena", Rol.DESPACHO));

        assertTrue(usuarios.autenticar("t5-ana", "buena").isPresent());
        assertFalse(usuarios.autenticar("t5-ana", "mala").isPresent());
        assertFalse(usuarios.autenticar("t5-nadie", "buena").isPresent());

        usuarios.cambiar(ana.getId(), null, null, false, null);
        assertFalse(usuarios.autenticar("t5-ana", "buena").isPresent(), "un usuario inactivo no entra");

        usuarios.cambiar(ana.getId(), null, null, true, "nueva");
        assertFalse(usuarios.autenticar("t5-ana", "buena").isPresent());
        assertTrue(usuarios.autenticar("t5-ana", "nueva").isPresent());
    }

    @Test
    void unaUrlPublicaDeLocalhostSeReconoceParaAvisar() {
        assertTrue(Usuarios.apuntaALocalhost("http://localhost:8080"));
        assertTrue(Usuarios.apuntaALocalhost("http://127.0.0.1:8080/"));
        assertTrue(Usuarios.apuntaALocalhost("HTTP://LOCALHOST"));
        assertFalse(Usuarios.apuntaALocalhost("https://modelos.puntotres.com"));
        assertFalse(Usuarios.apuntaALocalhost(null));
    }
}

package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.modelos.persistence.Dispositivo;
import com.puntotres.packinglist.modelos.persistence.DispositivoRepository;
import com.puntotres.packinglist.modelos.persistence.Rol;

/**
 * Alta de dispositivos con un código de un solo uso. Cada test usa sus propios
 * logins ({@code t5-...}): la base de datos en memoria es la misma para toda
 * la suite.
 */
@SpringBootTest
class DispositivosTest {

    @Autowired
    private Dispositivos dispositivos;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private DispositivoRepository repositorioDispositivos;

    @Test
    void cadaAltaEsUnAparatoConSuTokenYSoloSeGuardaElHash() {
        Long taller = usuarios.crear(new Usuarios.Alta("Taller Tánger", "t5-tanger", "clave-taller", Rol.TALLER)).getId();

        Dispositivos.TokenDispositivo tablet = dispositivos.darDeAlta(taller, "Tablet del taller");
        Dispositivos.TokenDispositivo movil = dispositivos.darDeAlta(taller, "Móvil de Bachir");

        assertEquals(taller, tablet.dispositivo().getUsuarioId());
        assertNotEquals(tablet.tokenEnClaro(), movil.tokenEnClaro());
        assertNotEquals(tablet.dispositivo().getId(), movil.dispositivo().getId());
        assertTrue(dispositivos.porToken(tablet.tokenEnClaro()).isPresent());
        assertTrue(dispositivos.porToken(movil.tokenEnClaro()).isPresent(), "la tablet y el móvil valen a la vez");

        Dispositivo guardado = repositorioDispositivos.findById(tablet.dispositivo().getId()).orElseThrow();
        assertNotEquals(tablet.tokenEnClaro(), guardado.getTokenHash(), "el token se guarda como hash");
    }

    @Test
    void sinUsuarioNoHayAlta() {
        assertThrows(IllegalArgumentException.class, () -> dispositivos.darDeAlta(999_999L, "Móvil"));
    }

    @Test
    void unDispositivoRevocadoNoSeEncuentraPorToken() {
        Long taller = usuarios.crear(new Usuarios.Alta("Taller Revocado", "t5-revocado", "clave-taller", Rol.TALLER)).getId();
        Dispositivos.TokenDispositivo token = dispositivos.darDeAlta(taller, "Móvil");

        dispositivos.revocar(token.dispositivo().getId());

        assertFalse(dispositivos.porToken(token.tokenEnClaro()).isPresent());
        assertTrue(repositorioDispositivos.findById(token.dispositivo().getId()).orElseThrow().revocado(),
                "revocar no lo borra: queda la traza");
    }

    @Test
    void apuntarElUsoNoDeshaceUnaRevocacionHechaEntreMedias() {
        Long taller = usuarios.crear(new Usuarios.Alta("Taller Carrera T6", "t6-carrera", "clave-taller", Rol.TALLER)).getId();
        Dispositivos.TokenDispositivo token = dispositivos.darDeAlta(taller, "Móvil");
        // Lo que ha leído el filtro de la cookie antes de que el admin lo revocara.
        Dispositivo leidoPorElFiltro = dispositivos.porToken(token.tokenEnClaro()).orElseThrow();

        dispositivos.revocar(token.dispositivo().getId());
        dispositivos.apuntarUso(leidoPorElFiltro);

        Dispositivo guardado = repositorioDispositivos.findById(token.dispositivo().getId()).orElseThrow();
        assertTrue(guardado.revocado(), "apuntar el uso no puede deshacer la revocación");
        assertFalse(dispositivos.porToken(token.tokenEnClaro()).isPresent());
    }

    @Test
    void apuntarElUsoEscribeElUltimoUsoDeUnDispositivoVivo() {
        Long taller = usuarios.crear(new Usuarios.Alta("Taller Uso T6", "t6-uso-vivo", "clave-taller", Rol.TALLER)).getId();
        Dispositivos.TokenDispositivo token = dispositivos.darDeAlta(taller, "Móvil");

        dispositivos.apuntarUso(dispositivos.porToken(token.tokenEnClaro()).orElseThrow());

        assertNotNull(repositorioDispositivos.findById(token.dispositivo().getId()).orElseThrow().getUltimoUso());
    }
}

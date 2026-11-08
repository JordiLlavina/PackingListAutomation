package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.modelos.persistence.CodigoAlta;
import com.puntotres.packinglist.modelos.persistence.CodigoAltaRepository;
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

    private static final String NO_VALE = "El código no vale o ha caducado";

    @Autowired
    private Dispositivos dispositivos;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private CodigoAltaRepository codigos;

    @Autowired
    private DispositivoRepository repositorioDispositivos;

    @Test
    void elCodigoDeAltaSeCanjeaUnaSolaVez() {
        Long taller = usuarios.crear(new Usuarios.Alta("Taller Tánger", "t5-tanger", "", Rol.TALLER)).getId();

        String codigo = dispositivos.generarCodigoAlta(taller);
        assertTrue(codigo.matches("\\d{6}"), codigo);

        Dispositivos.TokenDispositivo token = dispositivos.canjear(codigo, "Tablet del taller");
        assertEquals(taller, token.dispositivo().getUsuarioId());
        assertTrue(dispositivos.porToken(token.tokenEnClaro()).isPresent());

        Dispositivo guardado = repositorioDispositivos.findById(token.dispositivo().getId()).orElseThrow();
        assertNotEquals(token.tokenEnClaro(), guardado.getTokenHash(), "el token se guarda como hash");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> dispositivos.canjear(codigo, "Otro móvil"));
        assertEquals(NO_VALE, error.getMessage());
    }

    @Test
    void unCodigoNuevoAnulaElAnteriorDelMismoUsuario() {
        Long taller = usuarios.crear(new Usuarios.Alta("Taller Fez", "t5-fez", "", Rol.TALLER)).getId();

        String viejo = dispositivos.generarCodigoAlta(taller);
        String nuevo = dispositivos.generarCodigoAlta(taller);

        if (!viejo.equals(nuevo)) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> dispositivos.canjear(viejo, "Móvil"));
            assertEquals(NO_VALE, error.getMessage());
        }
        assertFalse(dispositivos.canjear(nuevo, "Móvil").tokenEnClaro().isBlank());
    }

    @Test
    void unCodigoCaducadoNoSeCanjea() {
        Long taller = usuarios.crear(new Usuarios.Alta("Taller Ubrique", "t5-ubrique", "", Rol.TALLER)).getId();
        // No son seis cifras a propósito: así ningún código generado por otro test puede coincidir.
        codigos.save(new CodigoAlta(taller, "T5CADU", LocalDateTime.now().minusMinutes(1)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> dispositivos.canjear("T5CADU", "Móvil"));
        assertEquals(NO_VALE, error.getMessage());
    }

    @Test
    void unDispositivoRevocadoNoSeEncuentraPorToken() {
        Long taller = usuarios.crear(new Usuarios.Alta("Taller Revocado", "t5-revocado", "", Rol.TALLER)).getId();
        Dispositivos.TokenDispositivo token = dispositivos.canjear(dispositivos.generarCodigoAlta(taller), "Móvil");

        dispositivos.revocar(token.dispositivo().getId());

        assertFalse(dispositivos.porToken(token.tokenEnClaro()).isPresent());
        assertTrue(repositorioDispositivos.findById(token.dispositivo().getId()).orElseThrow().revocado(),
                "revocar no lo borra: queda la traza");
    }
}

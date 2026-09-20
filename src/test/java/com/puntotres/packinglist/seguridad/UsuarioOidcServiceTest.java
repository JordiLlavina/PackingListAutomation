package com.puntotres.packinglist.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * El alta es automática y sin permisos, y la fila es la misma persona para
 * siempre aunque le cambien el correo.
 */
@SpringBootTest(properties = "seguridad.administradores-iniciales=jefa@puntotres.com")
class UsuarioOidcServiceTest {

    @Autowired
    private UsuarioOidcService servicio;

    @Autowired
    private UsuarioRepository repositorio;

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
    }

    @Test
    void laPrimeraEntradaCreaElUsuarioSinNingunPermiso() {
        Usuario creado = servicio.entrar("oid-1", "ana@puntotres.com", "Ana");

        assertEquals("ana@puntotres.com", creado.getCorreo());
        assertTrue(creado.isActivo());
        assertFalse(creado.isAdministrador(), "Entrar en el tenant no da acceso a nada");
        assertEquals(1, repositorio.count());
    }

    @Test
    void laSegundaEntradaNoDuplicaLaFila() {
        servicio.entrar("oid-1", "ana@puntotres.com", "Ana");
        servicio.entrar("oid-1", "ana@puntotres.com", "Ana");

        assertEquals(1, repositorio.count());
    }

    @Test
    void unCambioDeCorreoActualizaLaMismaFilaEnVezDeCrearOtra() {
        Usuario primera = servicio.entrar("oid-1", "ana.perez@puntotres.com", "Ana Pérez");
        Usuario segunda = servicio.entrar("oid-1", "ana.garcia@puntotres.com", "Ana García");

        // Es la prueba del oid: si la clave fuera el correo, el día que a
        // alguien le cambian el nombre de usuario entraría como persona nueva
        // sin permisos y su histórico se quedaría huérfano.
        assertEquals(primera.getId(), segunda.getId());
        assertEquals("ana.garcia@puntotres.com", segunda.getCorreo());
        assertEquals(1, repositorio.count());
    }

    @Test
    void unCorreoDeLaListaDeAdministradoresInicialesEntraComoAdministrador() {
        Usuario jefa = servicio.entrar("oid-2", "jefa@puntotres.com", "Jefa");

        assertTrue(jefa.isAdministrador());
    }

    @Test
    void laListaDeAdministradoresInicialesSoloSeAplicaEnElAlta() {
        Usuario jefa = servicio.entrar("oid-2", "jefa@puntotres.com", "Jefa");
        jefa.setAdministrador(false);
        repositorio.save(jefa);

        Usuario segundaEntrada = servicio.entrar("oid-2", "jefa@puntotres.com", "Jefa");

        // Si se aplicara en cada entrada, quitarle el administrador a alguien
        // de esa lista sería imposible desde la pantalla, y la lista mandaría
        // para siempre desde un fichero que nadie vuelve a mirar.
        assertFalse(segundaEntrada.isAdministrador());
    }

    @Test
    void cadaEntradaEstampaElUltimoAcceso() {
        Usuario primera = servicio.entrar("oid-1", "ana@puntotres.com", "Ana");
        java.time.OffsetDateTime antes = primera.getUltimoAcceso();

        Usuario segunda = servicio.entrar("oid-1", "ana@puntotres.com", "Ana");

        assertTrue(segunda.getUltimoAcceso().isAfter(antes)
                || segunda.getUltimoAcceso().isEqual(antes));
    }
}

package com.puntotres.packinglist.seguridad;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.actividad.EventoActividadRepository;

@SpringBootTest
@Transactional
class PermisosDelUsuarioTest {

    @Autowired
    private UsuarioRepository repositorio;

    @Autowired
    private EventoActividadRepository eventos;

    @BeforeEach
    void limpiar() {
        // Otros tests dejan su "oid-1" en la base compartida; aquí se borra
        // dentro de la transacción del test, que se deshace al terminar. El
        // flush es necesario: Hibernate manda los INSERT antes que los DELETE
        // pendientes, y el alta del test chocaría con la fila vieja.
        // Los eventos de actividad apuntan a los usuarios: se borran antes.
        eventos.deleteAll();
        repositorio.deleteAll();
        repositorio.flush();
    }

    @Test
    void unUsuarioNuevoNoPuedeAbrirNada() {
        Usuario ana = repositorio.save(new Usuario("oid-1", "ana@puntotres.com", "Ana"));

        for (Seccion seccion : Seccion.values()) {
            assertFalse(ana.puedeAbrir(seccion), "No debería poder abrir " + seccion);
        }
    }

    @Test
    void loConcedidoSeGuardaYSeVuelveALeer() {
        Usuario ana = new Usuario("oid-1", "ana@puntotres.com", "Ana");
        ana.conceder(Seccion.PACKING_LIST);
        ana.conceder(Seccion.TARAS);
        repositorio.saveAndFlush(ana);

        Usuario releida = repositorio.findByOidEntra("oid-1").orElseThrow();

        assertTrue(releida.puedeAbrir(Seccion.PACKING_LIST));
        assertTrue(releida.puedeAbrir(Seccion.TARAS));
        assertFalse(releida.puedeAbrir(Seccion.ESCANDALLOS));
    }

    @Test
    void elAdministradorAbreAdministracionSinQueSeLaConcedanYElRestoNo() {
        Usuario jefa = new Usuario("oid-2", "jefa@puntotres.com", "Jefa");
        jefa.setAdministrador(true);
        repositorio.saveAndFlush(jefa);

        assertTrue(jefa.puedeAbrir(Seccion.ADMINISTRACION));
        // Ser administrador es poder repartir permisos, no tenerlos todos: si
        // administrar abriera además todas las secciones, no habría forma de
        // tener a alguien que reparte sin darle acceso a todo.
        assertFalse(jefa.puedeAbrir(Seccion.ESCANDALLOS));
    }

    @Test
    void unUsuarioDesactivadoNoPuedeAbrirNadaAunqueLoTengaConcedido() {
        Usuario ana = new Usuario("oid-1", "ana@puntotres.com", "Ana");
        ana.conceder(Seccion.PACKING_LIST);
        ana.setActivo(false);
        repositorio.saveAndFlush(ana);

        assertFalse(ana.puedeAbrir(Seccion.PACKING_LIST));
    }
}

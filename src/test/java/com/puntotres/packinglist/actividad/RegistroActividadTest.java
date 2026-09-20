package com.puntotres.packinglist.actividad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.seguridad.Seccion;
import com.puntotres.packinglist.seguridad.Usuario;
import com.puntotres.packinglist.seguridad.UsuarioRepository;

@SpringBootTest
class RegistroActividadTest {

    @Autowired
    private RegistroActividad registro;

    @Autowired
    private EventoActividadRepository eventos;

    @Autowired
    private UsuarioRepository usuarios;

    @BeforeEach
    void limpiar() {
        eventos.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    void elEventoGuardaClienteCantidadYDetalle() {
        Usuario ana = usuarios.saveAndFlush(new Usuario("oid-1", "ana@puntotres.com", "Ana"));

        registro.registrarPara(ana, TipoEvento.PACKING_LIST_GENERADO, Seccion.PACKING_LIST,
                "APC", 5, "5 ficheros en 3 destinaciones");

        List<EventoActividad> todos = eventos.findAll();
        assertEquals(1, todos.size());
        EventoActividad evento = todos.get(0);
        assertEquals(TipoEvento.PACKING_LIST_GENERADO, evento.getTipo());
        assertEquals("APC", evento.getCliente());
        assertEquals(5, evento.getCantidad());
        assertEquals(Seccion.PACKING_LIST, evento.getSeccion());
        assertNotNull(evento.getMomento());
    }

    @Test
    void elCorreoSeCopiaEnElEventoAunqueElUsuarioEsteEnlazado() {
        Usuario ana = usuarios.saveAndFlush(new Usuario("oid-1", "ana.perez@puntotres.com", "Ana"));
        registro.registrarPara(ana, TipoEvento.ENTRADA, null, null, null, null);

        ana.setCorreo("ana.garcia@puntotres.com");
        usuarios.saveAndFlush(ana);

        // El informe de hace un año tiene que seguir diciendo quién era esa
        // persona ENTONCES. El usuarioId sirve para agrupar; el correo, para
        // leer.
        assertEquals("ana.perez@puntotres.com", eventos.findAll().get(0).getCorreo());
    }

    @Test
    void unFalloEscribiendoElEventoNoSePropaga() {
        // Sin usuario en la sesión y con datos imposibles, registrar no puede
        // lanzar: que no se pueda apuntar quién generó un documento no es
        // razón para no generarlo.
        registro.registrar(TipoEvento.PACKING_LIST_GENERADO, Seccion.PACKING_LIST,
                "x".repeat(5000), 1, "y".repeat(5000));
        // Si llega aquí sin excepción, el test pasa.
    }
}

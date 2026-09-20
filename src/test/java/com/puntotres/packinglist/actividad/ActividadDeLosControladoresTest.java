package com.puntotres.packinglist.actividad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.seguridad.Seccion;
import com.puntotres.packinglist.seguridad.Usuario;
import com.puntotres.packinglist.seguridad.UsuarioRepository;

/**
 * Cada acción con significado deja su evento, y el evento lleva lo que hace
 * falta para leerlo después: quién, qué sección y un detalle sin datos de
 * cliente.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActividadDeLosControladoresTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventoActividadRepository eventos;

    @Autowired
    private UsuarioRepository usuarios;

    @BeforeEach
    void sembrar() {
        eventos.deleteAll();
        usuarios.deleteAll();
        Usuario ana = new Usuario("oid-1", "ana@puntotres.com", "Ana");
        ana.conceder(Seccion.TARAS);
        usuarios.saveAndFlush(ana);
    }

    private OidcLoginRequestPostProcessor ana() {
        return oidcLogin().idToken(token -> token
                .claim("oid", "oid-1").claim("name", "Ana").claim("email", "ana@puntotres.com"));
    }

    @Test
    void editarUnaTaraDejaSuEvento() throws Exception {
        mockMvc.perform(post("/taras").with(ana()).with(csrf())
                .param("medida", "60x40x99").param("taraKg", "1.5"));

        List<EventoActividad> todos = eventos.findAll();
        assertEquals(1, todos.size());
        assertEquals(TipoEvento.TARA_EDITADA, todos.get(0).getTipo());
        assertTrue(todos.get(0).getDetalle().contains("60x40x99"));
        assertEquals("ana@puntotres.com", todos.get(0).getCorreo());
    }

    @Test
    void abrirUnaSeccionSinPermisoDejaSuEvento() throws Exception {
        mockMvc.perform(get("/clickup").with(ana()));

        List<EventoActividad> todos = eventos.findAll();
        assertEquals(1, todos.size());
        assertEquals(TipoEvento.ACCESO_DENEGADO, todos.get(0).getTipo());
        assertEquals(Seccion.ESCANDALLOS, todos.get(0).getSeccion());
    }
}

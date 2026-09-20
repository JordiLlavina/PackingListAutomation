package com.puntotres.packinglist.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.actividad.EventoActividadRepository;
import com.puntotres.packinglist.seguridad.Seccion;
import com.puntotres.packinglist.seguridad.Usuario;
import com.puntotres.packinglist.seguridad.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AdministracionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private EventoActividadRepository eventos;

    private Usuario jefa;
    private Usuario ana;

    @BeforeEach
    void sembrar() {
        // Los eventos de actividad apuntan a los usuarios: se borran antes.
        eventos.deleteAll();
        usuarios.deleteAll();
        Usuario nuevaJefa = new Usuario("oid-jefa", "jefa@puntotres.com", "Jefa");
        nuevaJefa.setAdministrador(true);
        jefa = usuarios.saveAndFlush(nuevaJefa);
        ana = usuarios.saveAndFlush(new Usuario("oid-ana", "ana@puntotres.com", "Ana"));
    }

    private OidcLoginRequestPostProcessor comoJefa() {
        return oidcLogin().idToken(token -> token
                .claim("oid", "oid-jefa").claim("name", "Jefa").claim("email", "jefa@puntotres.com"));
    }

    @Test
    void laTablaEnseniaATodosLosUsuarios() throws Exception {
        mockMvc.perform(get("/administracion/usuarios").with(comoJefa()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("ana@puntotres.com")));
    }

    @Test
    void concederUnaSeccionLaGuarda() throws Exception {
        mockMvc.perform(post("/administracion/usuarios").with(comoJefa()).with(csrf())
                        .param("filas[0].usuarioId", String.valueOf(ana.getId()))
                        .param("filas[0].activo", "true")
                        .param("filas[0].secciones", "TARAS")
                        .param("filas[1].usuarioId", String.valueOf(jefa.getId()))
                        .param("filas[1].activo", "true")
                        .param("filas[1].administrador", "true"))
                .andExpect(status().is3xxRedirection());

        assertTrue(usuarios.findByOidEntra("oid-ana").orElseThrow().puedeAbrir(Seccion.TARAS));
    }

    @Test
    void unAdministradorNoPuedeQuitarseASiMismoElAdministrador() throws Exception {
        mockMvc.perform(post("/administracion/usuarios").with(comoJefa()).with(csrf())
                        .param("filas[0].usuarioId", String.valueOf(jefa.getId()))
                        .param("filas[0].activo", "true"))
                .andExpect(status().is3xxRedirection());

        // Quedarse sin ningún administrador deja la aplicación sin forma de
        // conceder permisos salvo tocando la base de datos a mano.
        assertTrue(usuarios.findByOidEntra("oid-jefa").orElseThrow().isAdministrador());
    }

    @Test
    void unAdministradorNoPuedeDesactivarseASiMismo() throws Exception {
        mockMvc.perform(post("/administracion/usuarios").with(comoJefa()).with(csrf())
                        .param("filas[0].usuarioId", String.valueOf(jefa.getId()))
                        .param("filas[0].administrador", "true"))
                .andExpect(status().is3xxRedirection());

        assertTrue(usuarios.findByOidEntra("oid-jefa").orElseThrow().isActivo());
    }

    @Test
    void desactivarAOtroSiFunciona() throws Exception {
        mockMvc.perform(post("/administracion/usuarios").with(comoJefa()).with(csrf())
                        .param("filas[0].usuarioId", String.valueOf(ana.getId()))
                        .param("filas[1].usuarioId", String.valueOf(jefa.getId()))
                        .param("filas[1].activo", "true")
                        .param("filas[1].administrador", "true"))
                .andExpect(status().is3xxRedirection());

        assertFalse(usuarios.findByOidEntra("oid-ana").orElseThrow().isActivo());
    }
}

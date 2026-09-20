package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

/**
 * Esconder la tarjeta no es seguridad —la seguridad es el interceptor—: es no
 * enseñar puertas que no se pueden abrir.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MenuFiltradoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private EventoActividadRepository eventos;

    @BeforeEach
    void limpiar() {
        // Los eventos de actividad apuntan a los usuarios: se borran antes.
        eventos.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    void elMenuSoloPintaLasTarjetasConcedidas() throws Exception {
        Usuario ana = new Usuario("oid-1", "ana@puntotres.com", "Ana");
        ana.conceder(Seccion.ESCANDALLOS);
        usuarios.saveAndFlush(ana);

        mockMvc.perform(get("/menu").with(identidad()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/clickup")))
                .andExpect(content().string(not(containsString("/packing-list"))))
                .andExpect(content().string(not(containsString("/taras"))));
    }

    @Test
    void quienNoTieneNadaVeElMenuVacioYAQuienPedirlo() throws Exception {
        usuarios.saveAndFlush(new Usuario("oid-1", "ana@puntotres.com", "Ana"));

        mockMvc.perform(get("/menu").with(identidad()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no tienes ninguna sección")));
    }

    private static OidcLoginRequestPostProcessor identidad() {
        return oidcLogin().idToken(token -> token
                .claim("oid", "oid-1")
                .claim("name", "Ana")
                .claim("email", "ana@puntotres.com"));
    }
}

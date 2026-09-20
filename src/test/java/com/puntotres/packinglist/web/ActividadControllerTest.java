package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
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
import com.puntotres.packinglist.actividad.RegistroActividad;
import com.puntotres.packinglist.actividad.TipoEvento;
import com.puntotres.packinglist.seguridad.Seccion;
import com.puntotres.packinglist.seguridad.Usuario;
import com.puntotres.packinglist.seguridad.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ActividadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private EventoActividadRepository eventos;

    @Autowired
    private RegistroActividad registro;

    @BeforeEach
    void sembrar() {
        eventos.deleteAll();
        usuarios.deleteAll();
        Usuario jefa = new Usuario("oid-jefa", "jefa@puntotres.com", "Jefa");
        jefa.conceder(Seccion.ACTIVIDAD);
        usuarios.saveAndFlush(jefa);
        registro.registrarPara(jefa, TipoEvento.PACKING_LIST_GENERADO, Seccion.PACKING_LIST,
                "AMI", 4, "4 ficheros");
    }

    private OidcLoginRequestPostProcessor jefa() {
        return oidcLogin().idToken(token -> token
                .claim("oid", "oid-jefa").claim("name", "Jefa").claim("email", "jefa@puntotres.com"));
    }

    @Test
    void elDashboardSeAbreConLaSeccionConcedida() throws Exception {
        mockMvc.perform(get("/actividad").with(jefa()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AMI")));
    }

    @Test
    void elFiltroDeFechasSeAplica() throws Exception {
        mockMvc.perform(get("/actividad").with(jefa())
                        .param("desde", java.time.LocalDate.now().minusDays(90).toString())
                        .param("hasta", java.time.LocalDate.now().minusDays(60).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sin actividad")));
    }
}

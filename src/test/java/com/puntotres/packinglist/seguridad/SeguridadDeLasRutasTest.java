package com.puntotres.packinglist.seguridad;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sin identidad no se ve nada. Es lo que retira del README la frase de que la
 * aplicación no tiene usuarios y solo puede vivir dentro de la red.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeguridadDeLasRutasTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sinIdentidadElMenuMandaAlLoginDeMicrosoft() throws Exception {
        mockMvc.perform(get("/menu"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/oauth2/authorization/azure"));
    }

    @Test
    void sinIdentidadUnaPantallaDeTrabajoTampocoSeVe() throws Exception {
        mockMvc.perform(get("/packing-list"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void conIdentidadElMenuSeVe() throws Exception {
        mockMvc.perform(get("/menu").with(oidcLogin()))
                .andExpect(status().isOk());
    }

    @Test
    void laHojaDeEstilosSeSirveSinIdentidad() throws Exception {
        // Si el CSS exigiera identidad, la pantalla de error y la de "sin
        // permiso" saldrían sin estilos y parecerían rotas.
        mockMvc.perform(get("/estilo.css"))
                .andExpect(status().isOk());
    }
}

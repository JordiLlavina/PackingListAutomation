package com.puntotres.packinglist.seguridad;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Quién eres y cómo salir, en todas las pantallas: sin esto, en un PC
 * compartido del almacén la sesión de quien pasó antes sigue abierta y el
 * envío siguiente sale a nombre de otra persona.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CabeceraDeUsuarioTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void elMenuEnseniaElNombreDeQuienHaEntradoYElBotonDeSalir() throws Exception {
        mockMvc.perform(get("/menu").with(oidcLogin()
                        .idToken(token -> token
                                .claim("oid", "oid-1")
                                .claim("name", "Ana Pérez")
                                .claim("email", "ana@puntotres.com"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ana Pérez")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/salir")));
    }
}

package com.puntotres.packinglist.seguridad;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La aplicación no carga nada de ningún CDN, así que la CSP cierra todo a
 * 'self'. Lo que sí tiene es JavaScript y estilos EN LÍNEA en cuatro
 * plantillas (entrada, revisión, resultados y etiquetas de artículo), y por
 * eso script-src y style-src llevan 'unsafe-inline': sin ellos esas
 * pantallas dejan de funcionar y ningún test lo ve, solo la consola del
 * navegador. Quitarlos exige antes sacar ese código a ficheros propios.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CabecerasDeSeguridadTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void lasRespuestasLlevanLasCabecerasDeSeguridad() throws Exception {
        mockMvc.perform(get("/menu").with(oidcLogin()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; script-src 'self' 'unsafe-inline'; "
                                + "style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; "
                                + "base-uri 'self'; form-action 'self' https://login.microsoftonline.com"));
    }
}

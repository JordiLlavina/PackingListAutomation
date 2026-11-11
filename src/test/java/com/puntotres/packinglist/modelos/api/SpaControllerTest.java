package com.puntotres.packinglist.modelos.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La PWA vive en {@code /app} y enruta en el navegador: cualquier ruta suya
 * que se abra directamente (un marcador, un QR, recargar la página) tiene que
 * devolver el {@code index.html}, y un fichero con extensión se sirve tal
 * cual o es un 404, nunca el índice: un {@code .js} que no existe contestado
 * con HTML rompe la app con un error de sintaxis que no dice nada.
 *
 * No se mira el contenido del índice: con {@code -Dskip.frontend=true} el
 * build de la PWA no se ha hecho y el fichero puede no existir.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SpaControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void unaRutaDeLaAppDevuelveElIndice() throws Exception {
        mvc.perform(get("/app/modelos/12"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/app/index.html"));
    }

    @Test
    void laRaizDeLaAppDevuelveElIndice() throws Exception {
        mvc.perform(get("/app"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/app/index.html"));
        mvc.perform(get("/app/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/app/index.html"));
    }

    @Test
    void unFicheroQueNoExisteEsUn404YNoElIndice() throws Exception {
        mvc.perform(get("/app/assets/no-existe.js"))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null));
    }

    @Test
    void unFicheroQueExisteSeSirveTalCual() throws Exception {
        // src/test/resources/static/app/assets/prueba-spa.js
        mvc.perform(get("/app/assets/prueba-spa.js"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(null))
                .andExpect(header().string("Content-Type", Matchers.containsString("javascript")))
                .andExpect(content().string(Matchers.containsString("SpaControllerTest")));
    }

    @Test
    void noSeSaleDeLaCarpetaDeLaApp() throws Exception {
        mvc.perform(get("/app/assets/..%2F..%2Fapplication.yml"))
                .andExpect(status().is4xxClientError());
    }
}

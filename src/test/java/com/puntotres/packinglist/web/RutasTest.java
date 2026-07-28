package com.puntotres.packinglist.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RutasTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void laRaizLlevaAlMenu() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void elMenuMuestraLasDosSecciones() throws Exception {
        mvc.perform(get("/menu"))
                .andExpect(status().isOk())
                .andExpect(view().name("menu"))
                // Que sean href de un enlace, no solo texto suelto en la página.
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("href=\"/packing-list\"")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("href=\"/etiquetas-articulo\"")));
    }

    @Test
    void elAsistenteDePackingListViveEnSuPropiaRuta() throws Exception {
        mvc.perform(get("/packing-list"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"));
    }

    @Test
    void sinEnvioEnCursoLaRevisionVuelveAlPasoUno() throws Exception {
        mvc.perform(get("/revision"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/packing-list"));
    }

    @Test
    void laPantallaDeEtiquetasDeArticuloOfreceElFormulario() throws Exception {
        mvc.perform(get("/etiquetas-articulo"))
                .andExpect(status().isOk())
                .andExpect(view().name("etiquetas-articulo"))
                // El desplegable y el input del excel de pedido.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cliente")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pedido")));
    }

    @Test
    void sinNadaGeneradoLosResultadosVuelvenAlFormulario() throws Exception {
        mvc.perform(get("/etiquetas-articulo/resultados"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/etiquetas-articulo"));
    }
}

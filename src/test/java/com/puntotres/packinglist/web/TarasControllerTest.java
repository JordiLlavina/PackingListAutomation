package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.persistence.CatalogoTarasJpa;

/**
 * Pesar un cartón tiene que poder hacerse desde la pantalla. Sin esto, mover
 * las taras a la base de datos habría empeorado las cosas: de editar un yml a
 * escribir SQL a mano.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TarasControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CatalogoTarasJpa catalogo;

    @Test
    void laPantallaListaLasTarasConocidas() throws Exception {
        catalogo.guardar("77x77x77", 2.0);

        mvc.perform(get("/taras"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("taras"))
                .andExpect(content().string(containsString("77x77x77")));
    }

    @Test
    void guardarUnaTaraNuevaLaDejaEnElCatalogo() throws Exception {
        mvc.perform(post("/taras").param("medida", "50x30x20").param("taraKg", "0.9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/taras"));

        assertEquals(0.9, catalogo.taraPara("50x30x20").orElseThrow());
    }

    @Test
    void corregirElPesoDeUnCartonYaConocidoLoActualiza() throws Exception {
        catalogo.guardar("55x35x25", 1.0);

        mvc.perform(post("/taras").param("medida", "55x35x25").param("taraKg", "1.4"));

        assertEquals(1.4, catalogo.taraPara("55x35x25").orElseThrow());
    }

    @Test
    void unaMedidaIlegibleNoEntraEnElCatalogo() throws Exception {
        mvc.perform(post("/taras").param("medida", "grande").param("taraKg", "1.0"))
                .andExpect(redirectedUrl("/taras"));

        assertTrue(catalogo.taraPara("grande").isEmpty(),
                "una medida que no es LxAnchoxAlto no sirve para calcular volumen ni altura");
    }

    @Test
    void unPesoNegativoNoEntraEnElCatalogo() throws Exception {
        mvc.perform(post("/taras").param("medida", "44x33x22").param("taraKg", "-1"));

        assertTrue(catalogo.taraPara("44x33x22").isEmpty());
    }
}

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

import com.puntotres.packinglist.config.TaraProperties;
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

    @Autowired
    private TaraProperties configuracion;

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

        assertEquals(0.9, catalogo.taraCartonPara("50x30x20").orElseThrow());
    }

    @Test
    void corregirElPesoDeUnCartonYaConocidoLoActualiza() throws Exception {
        catalogo.guardar("55x35x25", 1.0);

        mvc.perform(post("/taras").param("medida", "55x35x25").param("taraKg", "1.4"));

        assertEquals(1.4, catalogo.taraCartonPara("55x35x25").orElseThrow());
    }

    @Test
    void unaMedidaIlegibleNoEntraEnElCatalogo() throws Exception {
        mvc.perform(post("/taras").param("medida", "grande").param("taraKg", "1.0"))
                .andExpect(redirectedUrl("/taras"));

        assertTrue(catalogo.taraCartonPara("grande").isEmpty(),
                "una medida que no es LxAnchoxAlto no sirve para calcular volumen ni altura");
    }

    @Test
    void unPesoNegativoNoEntraEnElCatalogo() throws Exception {
        mvc.perform(post("/taras").param("medida", "44x33x22").param("taraKg", "-1"));

        assertTrue(catalogo.taraCartonPara("44x33x22").isEmpty());
    }

    /**
     * En la pantalla se teclea el cartón, pero la cuenta que se hace después le
     * suma los separadores. Si no lo dijera, quien vea un cartón de 1,06 y un
     * bruto 1,22 por encima del neto no tendría de dónde sacar la diferencia y
     * pensaría que la tara está mal tecleada.
     *
     * Los números se leen de la configuración y no se fijan aquí: lo que pesa
     * un separador es un dato del almacén, igual que la tara, y se corrige
     * cuando se vuelve a pesar.
     */
    @Test
    void laPantallaDiceQueAdemasSeSumanLosSeparadores() throws Exception {
        mvc.perform(get("/taras"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("separadoresPorCaja",
                        configuracion.getSeparadoresCarton().getPorCaja()))
                .andExpect(model().attribute("pesoSeparadorKg",
                        configuracion.getSeparadoresCarton().getPesoKg()))
                .andExpect(model().attribute("pesoSeparadoresKg", catalogo.pesoSeparadoresKg()))
                .andExpect(content().string(containsString("separadores")));
    }
}

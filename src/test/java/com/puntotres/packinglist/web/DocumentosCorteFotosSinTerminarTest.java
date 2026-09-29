package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.service.corte.ConversionesDePrueba;
import com.puntotres.packinglist.service.corte.ConversorFotos;

/**
 * Generar con las fotos todavía preparándose. En el servidor Linux todas las
 * HEIC van por el camino Java (5-14 s cada una): esperar dentro de la
 * petición podía ser media hora, más que lo que aguanta un proxy o el
 * navegador, y mientras tanto la sesión caducaba debajo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentosCorteFotosSinTerminarTest {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConversorFotos conversor;

    private final MockHttpSession sesion = new MockHttpSession();

    @AfterEach
    void borrarLoDeLaSesion() throws Exception {
        mvc.perform(get("/documentos-corte/nuevo").session(sesion));
    }

    @Test
    void generarConLasFotosAMedioPrepararVuelveALaTablaSinEsperarNiPerderLoTecleado()
            throws Exception {
        when(conversor.convertir(any(), any())).thenReturn(ConversionesDePrueba.sinTerminar(2));
        mvc.perform(multipart("/documentos-corte/cargar")
                .file(new MockMultipartFile("pedido", "pedido.xlsx", XLSX,
                        DocumentosCorteControllerTest.pedidoAmi("ULL201")))
                .file(new MockMultipartFile("fotos", "H26.zip", "application/zip",
                        DocumentosCorteControllerTest.zipFotos("ULL201")))
                .param("cliente", "AMI").param("temporada", "H26").session(sesion));

        assertTimeoutPreemptively(Duration.ofSeconds(20), () ->
                mvc.perform(post("/documentos-corte/generar").session(sesion)
                                .param("filas[0].nombrePiel", "Box sin esperar"))
                        .andExpect(redirectedUrl("/documentos-corte/pieles"))
                        .andExpect(flash().attribute("error", containsString("preparando"))));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("value=\"Box sin esperar\"")))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s).*id=\"botonGenerar\"[^>]*disabled.*")));
    }
}

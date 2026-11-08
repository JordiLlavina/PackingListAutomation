package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** El flujo web de las etiquetas de artículo de SMS, con los beans reales. */
@SpringBootTest
@AutoConfigureMockMvc
class EtiquetasSmsControllerTest {

    @Autowired
    private MockMvc mvc;

    private final MockHttpSession sesion = new MockHttpSession();

    private static MockMultipartFile real() throws IOException {
        try (InputStream entrada = EtiquetasSmsControllerTest.class
                .getResourceAsStream("/ejemplos/corte/COMMANDE_SMS_APC_FALL27.xlsx")) {
            return new MockMultipartFile("excel", "COMMANDE SMS_FALL27_PUNTOTRES.xlsx",
                    "application/octet-stream", entrada.readAllBytes());
        }
    }

    @Test
    void elMenuDeEtiquetasLlevaALasDeSmsYPrototiposSigueEnDesarrollo() throws Exception {
        String menu = mvc.perform(get("/etiquetas-articulo")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(menu.contains("href=\"/etiquetas-articulo-sms\""), menu);
        assertTrue(menu.contains("Prototipos") && menu.contains("En desarrollo"), menu);
    }

    @Test
    void soloApcSePuedeElegirLosDemasEnDesarrollo() throws Exception {
        String pagina = mvc.perform(get("/etiquetas-articulo-sms")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(pagina.matches("(?s).*<option value=\"APC\"(?![^>]*disabled)[^>]*selected[^>]*>A\\.P\\.C\\.</option>.*"),
                pagina);
        assertTrue(pagina.matches("(?s).*<option value=\"AMI\"[^>]*disabled[^>]*>AMI — en desarrollo</option>.*"),
                pagina);
    }

    @Test
    void generaElWordYLoDescarga() throws Exception {
        mvc.perform(multipart("/etiquetas-articulo-sms/generar").file(real())
                        .param("cliente", "APC").param("temporada", "FALL 27").session(sesion))
                .andExpect(redirectedUrl("/etiquetas-articulo-sms/resultados"));

        mvc.perform(get("/etiquetas-articulo-sms/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("APC FALL 27 - Etiquetas SMS.docx")));

        byte[] word = mvc.perform(get("/etiquetas-articulo-sms/descargar").session(sesion))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("Etiquetas")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word))) {
            assertEquals("PUNTOTRES", doc.getTables().get(0).getRow(0).getCell(0).getParagraphs().get(0).getText());
            assertEquals("SMS", doc.getTables().get(0).getRow(0).getCell(0).getParagraphs().get(2).getText());
        }
    }

    @Test
    void sinTemporadaOConOtroClienteVuelveALaEntradaConElMotivo() throws Exception {
        mvc.perform(multipart("/etiquetas-articulo-sms/generar").file(real())
                        .param("cliente", "APC").param("temporada", " ").session(sesion))
                .andExpect(redirectedUrl("/etiquetas-articulo-sms"))
                .andExpect(flash().attribute("error", containsString("temporada")));
        mvc.perform(multipart("/etiquetas-articulo-sms/generar").file(real())
                        .param("cliente", "AMI").param("temporada", "FALL 27").session(sesion))
                .andExpect(redirectedUrl("/etiquetas-articulo-sms"))
                .andExpect(flash().attribute("error", containsString("en desarrollo")));
    }

    @Test
    void sinNadaGeneradoLosResultadosVuelvenALaEntrada() throws Exception {
        mvc.perform(get("/etiquetas-articulo-sms/resultados").session(new MockHttpSession()))
                .andExpect(redirectedUrl("/etiquetas-articulo-sms"));
        mvc.perform(get("/etiquetas-articulo-sms/descargar").session(new MockHttpSession()))
                .andExpect(status().isNotFound());
    }
}

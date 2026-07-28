package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Flujo web del procesado de escandallos con los beans reales.
 *
 * Lo que fija: la descarga es <strong>directa</strong> cuando no hay nada que
 * contar, y solo cuando hay avisos aparece la pantalla intermedia con el botón
 * de descargar igualmente.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EscandallosControllerTest {

    private static final String TIPO_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mvc;

    private static MockMultipartFile escandalloReal(String nombre) throws Exception {
        String ruta = "/ejemplos/escandallos/" + nombre;
        try (InputStream entrada = EscandallosControllerTest.class.getResourceAsStream(ruta)) {
            return new MockMultipartFile("escandallos", nombre, TIPO_XLSX, entrada.readAllBytes());
        }
    }

    private static MockMultipartFile basura(String nombre) {
        return new MockMultipartFile("escandallos", nombre, TIPO_XLSX,
                "esto no es un excel".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void laPantallaDeEntradaPideLosExcelsDeEscandallo() throws Exception {
        mvc.perform(get("/escandallos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escandallos ICSUITE")));
    }

    @Test
    void elMenuEnlazaLaPantallaDeEscandallos() throws Exception {
        mvc.perform(get("/menu"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/escandallos")));
    }

    @Test
    void sinAvisosSeDescargaElExcelDirectamente() throws Exception {
        byte[] excel = mvc.perform(multipart("/escandallos/procesar")
                        .file(escandalloReal("ULL770 NOIR.xlsx"))
                        .file(escandalloReal("ULL770 SAND.xlsx")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("Escandallos ICSUITE.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals(2, libro.getNumberOfSheets());
            assertEquals("ULL770.AL245 NOIR", libro.getSheetName(0));
        }
    }

    @Test
    void conAvisosSeVuelveALaPantallaConLaListaYElBotonDeDescargar() throws Exception {
        mvc.perform(multipart("/escandallos/procesar")
                        .file(escandalloReal("ULL770 NOIR.xlsx"))
                        .file(basura("roto.xlsx"))
                        .session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("roto.xlsx")))
                .andExpect(content().string(containsString("ULL770.AL245 NOIR")))
                .andExpect(content().string(containsString("/escandallos/descargar")));
    }

    @Test
    void elExcelConAvisosSePuedeDescargarDespuesDesdeLaSesion() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(multipart("/escandallos/procesar")
                        .file(escandalloReal("ULL770 NOIR.xlsx"))
                        .file(basura("roto.xlsx"))
                        .session(sesion))
                .andExpect(status().isOk());

        byte[] excel = mvc.perform(get("/escandallos/descargar").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals(1, libro.getNumberOfSheets());
        }
    }

    @Test
    void siNingunFicheroEsUtilizableSeQuedaEnLaPantallaConElError() throws Exception {
        mvc.perform(multipart("/escandallos/procesar").file(basura("roto.xlsx")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("roto.xlsx")))
                .andExpect(content().string(containsString("No se ha podido procesar")));
    }

    @Test
    void sinFicherosSeAvisaEnVezDeGenerarUnExcelVacio() throws Exception {
        mvc.perform(multipart("/escandallos/procesar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ningún excel de escandallo")));
    }

    @Test
    void descargarSinNadaEnSesionVuelveALaPantalla() throws Exception {
        mvc.perform(get("/escandallos/descargar").session(new MockHttpSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/escandallos"));
    }
}

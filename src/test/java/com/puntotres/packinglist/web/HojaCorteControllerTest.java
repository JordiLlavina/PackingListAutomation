package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.puntotres.packinglist.persistence.ArchivoHojasCorte;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoColor;

/** La Hoja de corte de un modelo: formulario, catálogo, generar e histórico. */
@SpringBootTest
@AutoConfigureMockMvc
class HojaCorteControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private ArchivoHojasCorte archivo;

    @Test
    void elMenuDelCorteTieneLaTerceraTarjetaYElFormularioArrancaConUnColor() throws Exception {
        mvc.perform(get("/documentos-corte"))
                .andExpect(content().string(containsString("href=\"/documentos-corte/hoja\"")))
                .andExpect(content().string(containsString("Hoja de corte")));
        mvc.perform(get("/documentos-corte/hoja"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"lineas[0].cantidad\"")))
                .andExpect(content().string(containsString("Empezar desde cero")));
    }

    @Test
    void elCatalogoDevuelveLaPielElForroYLasCombinacionesConSusColores() throws Exception {
        catalogo.asegurarArticulo("AMI", "ULL7H01.AL7H01");
        catalogo.guardarCampo("AMI", "ULL7H01.AL7H01", CampoArticulo.FORRO, "STILLA", false);
        catalogo.guardarCampo("AMI", "ULL7H01.AL7H01", CampoArticulo.COMBINACION1, "NOBILE", false);
        catalogo.guardarCampoColor("AMI", "ULL7H01.AL7H01", "7H1", CampoColor.COLOR_FORRO, "999");
        catalogo.guardarCampoColor("AMI", "ULL7H01.AL7H01", "7H1", CampoColor.COLOR_COMBINACION1, "ORO");

        String json = mvc.perform(get("/documentos-corte/hoja/catalogo").param("cliente", "AMI"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(json.matches("(?s).*\"referencia\":\"ULL7H01.AL7H01\",\"codigoColor\":\"7H1\".*\"forro\":\"STILLA\","
                + "\"colorForro\":\"999\",\"combinaciones\":\\[\\{\"(nombre\":\"NOBILE\",\"color\":\"ORO|color\":\"ORO\","
                + "\"nombre\":\"NOBILE)\"}].*"), json);
    }

    @Test
    void generarDevuelveElExcelLoGuardaYSePuedeBajarEImportar() throws Exception {
        String id = mvc.perform(generar().param("lineas[0].cantidad", "12").param("lineas[1].cantidad", ""))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("AMI%20SS27%20-%20ULL7H02%20-%20Hoja%20de%20Corte.xlsx")))
                .andReturn().getResponse().getHeader("X-Hoja-Id");

        mvc.perform(get("/documentos-corte/hoja/antiguas"))
                .andExpect(content().string(containsString("ULL7H02")))
                .andExpect(content().string(containsString("/documentos-corte/hoja/antiguas/" + id + "/xlsx")));
        byte[] excel = mvc.perform(get("/documentos-corte/hoja/antiguas/" + id + "/xlsx"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals(1, libro.getNumberOfSheets(), "la línea sin cantidad no sale");
            assertEquals("ULL7H02.AL7H02 - 001 BLACK", libro.getSheetAt(0).getSheetName());
        }
        assertEquals("NOBILE", archivo.datosDe(Long.valueOf(id)).orElseThrow().lineas().get(0)
                .combinaciones().get(0).nombre());
        mvc.perform(get("/documentos-corte/hoja").param("importar", id))
                .andExpect(content().string(containsString("value=\"ULL7H02.AL7H02\"")))
                .andExpect(content().string(containsString("value=\"NOBILE\"")));
    }

    @Test
    void sinNingunaLineaConCantidadEs400YNoGuardaNada() throws Exception {
        int antes = archivo.todas().size();

        mvc.perform(generar().param("lineas[0].cantidad", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Ninguna línea tiene cantidad")));

        assertEquals(antes, archivo.todas().size());
    }

    @Test
    void elCatalogoDeUnClienteSinArticulosEstaVacio() throws Exception {
        mvc.perform(get("/documentos-corte/hoja/catalogo").param("cliente", "NADIE7H"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articulos").isEmpty());
    }

    private MockMultipartHttpServletRequestBuilder generar() {
        MockMultipartHttpServletRequestBuilder peticion = multipart("/documentos-corte/hoja/generar");
        peticion.param("cliente", "AMI").param("temporada", "SS27").param("modelo", "ULL7H02")
                .param("referencia", "ULL7H02.AL7H02").param("nombreBolso", "Candy pouch")
                .param("lineas[0].codigoColor", "001").param("lineas[0].descripcionColor", "BLACK")
                .param("lineas[0].piel", "ALPACA").param("lineas[0].colorPiel", "NEGRO")
                .param("lineas[0].forro", "STILLA").param("lineas[0].colorForro", "999")
                .param("lineas[0].combinaciones[0].nombre", "NOBILE")
                .param("lineas[0].combinaciones[0].color", "ORO")
                .param("lineas[1].codigoColor", "2221").param("lineas[1].piel", "ALPACA");
        return peticion;
    }
}

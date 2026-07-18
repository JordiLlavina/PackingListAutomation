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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests del asistente web con los beans reales (los servicios de dominio no
 * tienen dependencias externas y la plantilla AMI está en el classpath).
 * Usa el mismo JSON de fixture que EnvioImportServiceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PackingListControllerTest {

    private static final String FICHERO_PARIS_USL728 = "PKL_PARIS_USL728.AL217_NOIR.xlsx";

    @Autowired
    private MockMvc mvc;

    private String jsonDePrueba() throws Exception {
        try (var in = getClass().getResourceAsStream("/client-packinglist/packing_list_ami_test.json")) {
            return new String(in.readAllBytes());
        }
    }

    /** POST /importar con el JSON de fixture y cabecera válida. */
    private void importar(MockHttpSession sesion) throws Exception {
        mvc.perform(post("/importar").session(sesion)
                        .param("json", jsonDePrueba())
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-26-1189")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/revision"));
    }

    @Test
    void laPantallaDeEntradaRenderizaElFormulario() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"))
                .andExpect(content().string(containsString("JSON del envío")));
    }

    @Test
    void importarConJsonValidoLlevaALaRevisionConSusAvisos() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("revision"))
                // Los avisos que el fixture contiene a propósito.
                .andExpect(content().string(containsString("1597")))
                .andExpect(content().string(containsString("1897")))
                .andExpect(content().string(containsString("más de una vez")))
                // Las tres destinaciones con sus cajas.
                .andExpect(content().string(containsString("PARIS")))
                .andExpect(content().string(containsString("JAPAN")))
                .andExpect(content().string(containsString("CHINA")));
    }

    @Test
    void importarConJsonInvalidoVuelveALaEntradaConElTextoPreservado() throws Exception {
        mvc.perform(post("/importar")
                        .param("json", "{esto no es json")
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"))
                .andExpect(model().attributeExists("errorJson"))
                .andExpect(content().string(containsString("{esto no es json")));
    }

    @Test
    void importarConFechaMalFormadaFallaLaValidacionDelCampo() throws Exception {
        mvc.perform(post("/importar")
                        .param("json", "{}")
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-1")
                        .param("fechaFactura", "2026-07-10")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"))
                .andExpect(model().attributeHasFieldErrors("envioForm", "fechaFactura"))
                .andExpect(content().string(containsString("dd/MM/yyyy")));
    }

    @Test
    void revisionSinEnvioEnCursoRedirigeALaEntrada() throws Exception {
        mvc.perform(get("/revision"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void recalcularConUnPesoManualInfiereElRestoDeSuReferencia() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        // Bruto de la caja 1 de PARIS (50 uds, 60x40x40, tara 1.6) puesto a
        // mano: peso unitario (51.6-1.6)/50 = 1.0 -> la caja 32 (47 uds)
        // debe quedar con neto 47.0 y bruto 48.6.
        mvc.perform(post("/recalcular").session(sesion)
                        .param("pesos[0].indiceDestino", "0")
                        .param("pesos[0].numeroCaja", "1")
                        .param("pesos[0].pesoBrutoKg", "51.6"))
                .andExpect(redirectedUrl("/revision"));

        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"47.0\"")))
                .andExpect(content().string(containsString("value=\"48.6\"")));
    }

    @Test
    void flujoCompletoGeneraDescargaYComprimeLosExcels() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        mvc.perform(post("/generar").session(sesion))
                .andExpect(redirectedUrl("/resultados"));

        // PARIS tiene 3 combinaciones referencia+color, JAPAN 2 y CHINA 1.
        mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("resultados"))
                .andExpect(content().string(containsString(FICHERO_PARIS_USL728)))
                .andExpect(content().string(containsString("PKL_PARIS_USL737.ACO137_ROJO_PASION_69.xlsx")))
                .andExpect(content().string(containsString("PKL_CHINA_USL737.ACO137_NOIR.xlsx")));

        byte[] excel = mvc.perform(get("/descargar/" + FICHERO_PARIS_USL728).session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = wb.getSheet("STANDARD PKL H26");
            assertEquals("PARIS", hoja.getRow(9).getCell(1).getStringCellValue());       // B10
            assertEquals("USL728.AL217", hoja.getRow(19).getCell(2).getStringCellValue()); // C20
        }

        byte[] zip = mvc.perform(get("/descargar-todo").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andReturn().getResponse().getContentAsByteArray();
        List<String> entradas = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entrada = in.getNextEntry(); entrada != null; entrada = in.getNextEntry()) {
                entradas.add(entrada.getName());
            }
        }
        assertEquals(6, entradas.size());
        assertTrue(entradas.contains(FICHERO_PARIS_USL728));
    }

    @Test
    void descargarUnFicheroQueNoExisteDevuelve404() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);
        mvc.perform(post("/generar").session(sesion));

        mvc.perform(get("/descargar/NO_EXISTE.xlsx").session(sesion))
                .andExpect(status().isNotFound());
    }
}

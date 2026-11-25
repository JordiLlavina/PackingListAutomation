package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.awt.Color;
import java.io.ByteArrayInputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.puntotres.packinglist.model.DatosHojaProduccion;
import com.puntotres.packinglist.persistence.ArchivoHojasProduccion;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.testutil.FotosDePrueba;

/**
 * Las pantallas de Hojas de Producción: el formulario, el catálogo que carga,
 * generar (que guarda y descarga el excel), las antiguas e importar una al
 * formulario. Lo que hay que anclar es que lo generado quede GUARDADO tal como
 * se tecleó, con el número de su taller y su foto, y que una hoja antigua
 * vuelva entera al formulario.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HojasProduccionControllerTest {

    private static final String TIPO_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArchivoHojasProduccion archivo;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private FotosCatalogo fotos;

    @Test
    void elFormularioSeAbreConLosDesplegablesElPanelDeFotoYElPrimerColor() throws Exception {
        String html = mvc.perform(get("/hojas-produccion"))
                .andExpect(status().isOk())
                .andExpect(view().name("hojas-produccion"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("MADE IN MOROCCO"), "el marcado interior");
        assertTrue(html.contains("ORO VIEJO"), "los acabados de metal");
        assertTrue(html.contains("Gütermann"), "las marcas de hilo");
        assertTrue(html.contains("C&amp;C LEATHER"), "los talleres conocidos");
        assertTrue(html.contains("name=\"lineas[0].cmHilo\""), "los cm del hilo");
        assertTrue(html.contains("name=\"modelo\""), "el modelo, uno por hoja, en la cabecera");
        assertFalse(html.contains("lineas[0].modelo"), "y ya no en cada línea");
        assertTrue(html.contains("name=\"fotoSubida\""), "se puede subir una foto");
        assertTrue(html.contains("id=\"temporadaGuardada\""), "la temporada guardada del cliente");
        assertTrue(html.contains("+ Añadir color"));
        assertFalse(html.contains("Lo propone el taller"));
        assertTrue(html.contains("ANTONIO MUÑIZ") && html.contains("MADE IN SPAIN"), "el marcado de cada taller");
    }

    @Test
    void elCatalogoDevuelveLosArticulosTabuladosYLasPielesDelModelo() throws Exception {
        catalogo.asegurarArticulo("AMI", "HPC050.HP0501");
        catalogo.guardarCampoModelo("AMI", "HPC050", CatalogoModelos.CampoModelo.NOMBRE, "BOLSO HPC");
        mvc.perform(get("/hojas-produccion/catalogo").param("cliente", "AMI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articulos[?(@.referencia == 'HPC050.HP0501')].codigoModelo").value("HPC050"))
                .andExpect(jsonPath("$.articulos[?(@.referencia == 'HPC050.HP0501')].texto",
                        org.hamcrest.Matchers.hasItem(containsString("BOLSO HPC"))))
                .andExpect(jsonPath("$.modelos.HPC050.pieles[0].nombre").value("HP0501"));
    }

    @Test
    void sinTallerOSinClienteOSinLineasNoSeGuardaNada() throws Exception {
        mvc.perform(hoja("APC", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("taller")));
        mvc.perform(hoja("NO-EXISTE", "HPC Sin cliente"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("cliente")));
        mvc.perform(multipart("/hojas-produccion/generar").param("cliente", "APC").param("taller", "HPC Vacía")
                        .param("lineas[0].marcadoNombre", "APC"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("ninguna línea")));
        assertEquals(1, archivo.siguienteNumero("HPC Vacía"), "la hoja rechazada no gastó número");
    }

    @Test
    void generarGuardaLaHojaConElNumeroDelTallerYDevuelveElExcel() throws Exception {
        MockHttpServletResponse primera = mvc.perform(hoja("APC", "HPC Sweet Bag"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(TIPO_XLSX))
                .andExpect(header().string("X-Numero", "1"))
                .andExpect(header().string("X-Siguiente-Numero", "2"))
                .andExpect(header().string("Content-Disposition", containsString("Hoja%20de%20producci")))
                .andReturn().getResponse();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(primera.getContentAsByteArray()))) {
            assertEquals("Hoja de producción", libro.getSheetName(0));
        }

        Long id = Long.valueOf(primera.getHeader("X-Hoja-Id"));
        DatosHojaProduccion guardada = archivo.datosDe(id).orElseThrow();
        assertEquals("APC", guardada.cliente());
        assertEquals("A.P.C.", guardada.nombreCliente(), "el nombre lo pone el servidor");
        assertEquals("HPC Sweet Bag", guardada.taller());
        assertEquals("F67104", guardada.modelo());
        assertEquals("LE NEIGE", guardada.nombreBolso());
        assertEquals(1, guardada.lineas().size(), "el segundo color, sin datos, se descarta");
        assertEquals(3, guardada.lineas().get(0).unidades());
        assertEquals("APC ORO", guardada.lineas().get(0).marcadoExterior());
        assertEquals("3", guardada.lineas().get(0).cmHilo());
        assertTrue(archivo.fotoDe(id).isEmpty(), "sin foto subida no se guarda ninguna");

        // La segunda del mismo taller sigue la numeración; un número tecleado manda.
        mvc.perform(hoja("APC", "HPC Sweet Bag")).andExpect(header().string("X-Numero", "2"));
        mvc.perform(hoja("APC", "HPC Sweet Bag").param("numero", "40"))
                .andExpect(header().string("X-Numero", "40"))
                .andExpect(header().string("X-Siguiente-Numero", "41"));
    }

    @Test
    void laFotoSubidaSeGuardaConLaHojaYSaleEnElExcel() throws Exception {
        MockMultipartFile foto = new MockMultipartFile("fotoSubida", "bolso.jpg", "image/jpeg",
                FotosDePrueba.jpeg(300, 200, Color.BLUE));
        MockHttpServletResponse respuesta = mvc.perform(hoja("APC", "HPC Foto").file(foto))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        Long id = Long.valueOf(respuesta.getHeader("X-Hoja-Id"));
        assertTrue(archivo.fotoDe(id).isPresent(), "la foto subida va con la hoja");
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(respuesta.getContentAsByteArray()))) {
            assertEquals(1, libro.getAllPictures().size(), "y sale en el excel");
        }
        mvc.perform(get("/hojas-produccion/antiguas/{id}/foto", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));

        // Importada y vuelta a generar sin tocar la foto, la nueva hereda la suya.
        String html = mvc.perform(get("/hojas-produccion").param("importar", String.valueOf(id)))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("name=\"fotoHojaId\" value=\"" + id + "\""));
        MockHttpServletResponse otra = mvc.perform(hoja("APC", "HPC Foto").param("fotoHojaId", String.valueOf(id)))
                .andReturn().getResponse();
        assertTrue(archivo.fotoDe(Long.valueOf(otra.getHeader("X-Hoja-Id"))).isPresent());
    }

    @Test
    void sinFotoSubidaElExcelLlevaLaPrincipalDelModeloEnElCatalogo() throws Exception {
        catalogo.asegurarArticulo("APC", "PXHPC-H99901");
        fotos.guardar("APC", "H99901", "principal.jpg", FotosDePrueba.jpeg(40, 40, Color.RED));
        MockHttpServletResponse respuesta = mvc.perform(hoja("APC", "HPC Catálogo").param("codigoModelo", "H99901"))
                .andReturn().getResponse();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(respuesta.getContentAsByteArray()))) {
            assertEquals(1, libro.getAllPictures().size());
        }
        Long id = Long.valueOf(respuesta.getHeader("X-Hoja-Id"));
        assertTrue(archivo.fotoDe(id).isEmpty(), "la del catálogo no se copia a la hoja");
        mvc.perform(get("/hojas-produccion/antiguas/{id}/xlsx", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(TIPO_XLSX));
    }

    @Test
    void lasAntiguasSeListanSeBajanYSeImportanAlFormulario() throws Exception {
        MockHttpServletResponse generada = mvc.perform(hoja("APC", "HPC Adrian")).andReturn().getResponse();
        Long id = Long.valueOf(generada.getHeader("X-Hoja-Id"));

        mvc.perform(get("/hojas-produccion/antiguas").param("id", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(view().name("hojas-produccion-antiguas"))
                .andExpect(content().string(containsString("F67104")))
                .andExpect(content().string(containsString("APC ORO")))
                .andExpect(content().string(containsString("Gütermann 181 · 3 CM")))
                .andExpect(content().string(containsString("/hojas-produccion?importar=" + id)));

        mvc.perform(get("/hojas-produccion/antiguas/{id}/xlsx", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(TIPO_XLSX));
        mvc.perform(get("/hojas-produccion/antiguas/{id}/xlsx", 99999999L))
                .andExpect(status().isNotFound());

        String html = mvc.perform(get("/hojas-produccion").param("importar", String.valueOf(id)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("value=\"F67104\""), "el modelo vuelve a la cabecera");
        assertTrue(html.contains("value=\"BADANA\""), "y el color a su línea");
        assertTrue(html.contains("value=\"HPC Adrian\""));
        assertTrue(html.contains("Usar el siguiente"), "conserva su número y ofrece el siguiente libre");

        mvc.perform(get("/hojas-produccion").param("importar", "99999999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ya no existe")));
    }

    private static MockMultipartHttpServletRequestBuilder hoja(String cliente, String taller) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/hojas-produccion/generar")
                .param("cliente", cliente)
                .param("temporada", "SMS FALL 27")
                .param("modelo", "F67104")
                .param("nombreBolso", "LE NEIGE")
                .param("taller", taller)
                .param("fechaSalida", "2026-11-24")
                .param("lineas[0].unidades", "3")
                .param("lineas[0].pielBase", "BADANA")
                .param("lineas[0].colorBase", "NOIR")
                .param("lineas[0].marcaHilo", "Gütermann")
                .param("lineas[0].numeroHilo", "181")
                .param("lineas[0].cmHilo", "3")
                .param("lineas[0].marcadoInterior", "MADE IN MOROCCO")
                .param("lineas[0].marcadoNombre", "APC")
                .param("lineas[0].marcadoColor", "ORO")
                .param("lineas[0].marcadoTamano", "")
                .param("lineas[0].fornitura", "-")
                .param("lineas[0].forro", "SARJA 005")
                .param("lineas[0].tinta", "CH 1999")
                .param("lineas[1].marcadoNombre", "APC")
                .param("lineas[1].marcadoInterior", "MADE IN MOROCCO");
    }
}

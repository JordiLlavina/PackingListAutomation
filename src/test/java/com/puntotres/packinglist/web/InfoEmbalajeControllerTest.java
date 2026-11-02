package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.puntotres.packinglist.model.DatosHojaEmbalaje;
import com.puntotres.packinglist.model.DatosHojaEmbalaje.DestinacionHoja;
import com.puntotres.packinglist.persistence.ArchivoHojasEmbalaje;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.HojaEmbalaje;
import com.puntotres.packinglist.persistence.TemporadaGuardada;

/**
 * Las pantallas de «Info de embalaje para taller»: cargar el pedido, rellenar
 * el formulario, generar (que guarda y descarga) y volver a una hoja antigua.
 *
 * Lo que de verdad hay que anclar es que lo generado quede GUARDADO tal como
 * se tecleó —la pantalla de antiguos y la importación viven de ese snapshot—
 * y que importar una hoja cuya temporada ya no existe no deje al usuario sin
 * formulario.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InfoEmbalajeControllerTest {

    private static final String TIPO_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String TIPO_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArchivoHojasEmbalaje archivo;

    @Autowired
    private ArchivoTemporadas temporadas;

    private static byte[] recurso(String ruta) throws Exception {
        try (var entrada = InfoEmbalajeControllerTest.class.getResourceAsStream(ruta)) {
            return entrada.readAllBytes();
        }
    }

    private static MockMultipartFile pedidoApc() throws Exception {
        return new MockMultipartFile("pedido", "APC_PEDIDO_SPRING27.xlsx", TIPO_XLSX,
                recurso("/ejemplos/APC_PEDIDO_SPRING27.xlsx"));
    }

    /** Carga el pedido real de APC en una sesión nueva y la devuelve. */
    private MockHttpSession cargarApc() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(multipart("/info-embalaje/cargar")
                        .file(pedidoApc())
                        .param("cliente", "APC")
                        .param("temporada", "Spring 27")
                        .session(sesion))
                .andExpect(redirectedUrl("/info-embalaje/formulario"));
        return sesion;
    }

    /** El POST de generar con los datos de la foto de Tánger y dos destinaciones. */
    private static MockHttpServletRequestBuilder generar(MockHttpSession sesion, String nota) {
        return post("/info-embalaje/generar").session(sesion)
                .param("taller", "TÁNGER")
                .param("numeroEnvio", "270")
                .param("nota", nota)
                .param("referencia", "PXCBC-F67008")
                .param("codigoColor", "LZZ")
                .param("descripcionColor", "NOIR")
                .param("nombreBolso", "SAC LE NEIGE")
                .param("codigoModelo", "F67008")
                .param("medidaCaja", "60x40x40")
                .param("unidadesPorCaja", "10")
                .param("bolsaTela", "BOLSA TELA BEIGE 40x50")
                .param("etiquetaInterior", "PARIS")
                .param("instruccionEspecial", "GRABADO LOGO NIKEL")
                .param("destinos[0].destino", "JAPAN")
                .param("destinos[0].pedido", "4100132915")
                .param("destinos[0].cantidad", "120")
                .param("destinos[1].destino", "KOREA")
                .param("destinos[1].pedido", "4100132916")
                .param("destinos[1].cantidad", "80")
                .param("repartoCajas", "12 cajas de 10\n8 cajas de 10")
                .param("comentarios", "Cuidado con las asas")
                .param("enlaceVideo", "  https://youtu.be/guia  ");
    }

    private Long idGenerado(MockHttpServletResponse respuesta) {
        return Long.valueOf(respuesta.getHeader("X-Hoja-Id"));
    }

    @Test
    void laEntradaOfreceClientesYSoloAmiYApcDisponibles() throws Exception {
        String html = mvc.perform(get("/info-embalaje"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("value=\"AMI\""), html);
        assertTrue(html.contains("value=\"APC\""), html);
        // Los genéricos salen, pero apagados y «en desarrollo».
        assertTrue(html.contains("en desarrollo"), "algún cliente sin lector de pedido");
        assertTrue(html.matches("(?s).*<option[^>]*value=\"AMI\"(?![^>]*disabled)[^>]*>.*"), "AMI encendido");
        assertTrue(html.matches("(?s).*<option[^>]*value=\"APC\"(?![^>]*disabled)[^>]*>.*"), "APC encendido");
        assertTrue(html.contains("href=\"/info-embalaje/antiguos\""), "botón Importar antiguos");
        assertTrue(html.contains("href=\"/temporadas\""), "lápiz a las temporadas");
    }

    @Test
    void cargarConElExcelRealLlevaAlFormularioConLosArticulos() throws Exception {
        MockHttpSession sesion = cargarApc();

        String html = mvc.perform(get("/info-embalaje/formulario").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("PXCBC-F67008"), "la referencia en el desplegable");
        assertTrue(html.contains("4100132915"), "el Document d'achat en los datos del artículo");
        // Los artículos van al navegador como JSON con sus campos, no solo la etiqueta.
        assertTrue(html.contains("\"referencia\":\"PXCBC-F67008\""), "artículos serializados");
        assertTrue(html.contains("href=\"/comentarios-embalaje\""), "lápiz a los comentarios");
        assertTrue(html.contains("<datalist id=\"talleres\""), "sugerencias de taller");
        assertTrue(html.contains("href=\"/info-embalaje\""), "empezar de nuevo");
    }

    @Test
    void elFormularioSinPedidoCargadoVuelveALaEntrada() throws Exception {
        mvc.perform(get("/info-embalaje/formulario").session(new MockHttpSession()))
                .andExpect(redirectedUrl("/info-embalaje"));
    }

    @Test
    void cargarSinExcelNiTemporadaVuelveConError() throws Exception {
        mvc.perform(multipart("/info-embalaje/cargar").param("cliente", "APC"))
                .andExpect(redirectedUrl("/info-embalaje"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("error", containsString("Falta el excel de pedido")));
    }

    @Test
    void generarGuardaLaHojaYDevuelveElWord() throws Exception {
        MockHttpSession sesion = cargarApc();

        MockHttpServletResponse respuesta = mvc.perform(generar(sesion, "12662"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(TIPO_DOCX))
                .andExpect(header().string("X-Numero-Envio", "270"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("PXCBC-F67008")))
                .andReturn().getResponse();

        // Es un Word de verdad, no solo bytes con la cabecera puesta.
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(respuesta.getContentAsByteArray()))) {
            assertTrue(doc.getTables().size() > 0);
        }

        Long id = idGenerado(respuesta);
        assertTrue(archivo.todas().stream().anyMatch(hoja -> hoja.id().equals(id)
                && hoja.referencia().equals("PXCBC-F67008") && hoja.numeroEnvio() == 270));

        DatosHojaEmbalaje guardada = archivo.datosDe(id).orElseThrow();
        assertEquals("APC", guardada.cliente());
        assertEquals("Spring 27", guardada.temporada());
        assertEquals(LocalDate.now(), guardada.fecha());
        assertEquals("https://youtu.be/guia", guardada.enlaceVideo(), "el enlace, recortado");
        assertEquals(200, guardada.totalUnidades());

        mvc.perform(get("/info-embalaje/antiguos").param("id", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("12662")))
                .andExpect(content().string(containsString("GRABADO LOGO NIKEL")))
                .andExpect(content().string(containsString("Descargar Word")));
    }

    @Test
    void lasFilasDeDestinacionVaciasSeDescartan() throws Exception {
        MockHttpSession sesion = cargarApc();

        MockHttpServletResponse respuesta = mvc.perform(post("/info-embalaje/generar").session(sesion)
                        .param("numeroEnvio", "271")
                        .param("referencia", "PXCBC-F67008")
                        .param("codigoColor", "LZZ")
                        .param("destinos[0].destino", "JAPAN")
                        .param("destinos[0].pedido", "4100132915")
                        .param("destinos[0].cantidad", "")
                        .param("destinos[1].destino", "")
                        .param("destinos[1].pedido", "")
                        .param("destinos[1].cantidad", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        List<DestinacionHoja> destinaciones = archivo.datosDe(idGenerado(respuesta)).orElseThrow().destinaciones();
        assertEquals(1, destinaciones.size());
        assertEquals(new DestinacionHoja("JAPAN", "4100132915", 0), destinaciones.get(0),
                "una cantidad vacía es un cero, no un error");
    }

    @Test
    void generarSinPedidoCargadoNoGuardaNada() throws Exception {
        int antes = archivo.todas().size();
        mvc.perform(generar(new MockHttpSession(), "sin sesión"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("vuelve a cargar el pedido")));
        assertEquals(antes, archivo.todas().size());
    }

    @Test
    void importarPrerrellenaElFormulario() throws Exception {
        // El pedido sale de una temporada guardada, que sigue existiendo al importar.
        TemporadaGuardada temporada = temporadas.guardar(null, "APC", "Embalaje " + System.nanoTime(),
                "APC_PEDIDO_SPRING27.xlsx", recurso("/ejemplos/APC_PEDIDO_SPRING27.xlsx"));
        try {
            MockHttpSession sesion = new MockHttpSession();
            mvc.perform(multipart("/info-embalaje/cargar")
                            .param("cliente", "APC")
                            .param("temporadaGuardadaId", String.valueOf(temporada.getId()))
                            .session(sesion))
                    .andExpect(redirectedUrl("/info-embalaje/formulario"));
            Long id = idGenerado(mvc.perform(generar(sesion, "NOTA-IMPORT-77"))
                    .andExpect(status().isOk()).andReturn().getResponse());
            assertEquals(temporada.getId(), archivo.hoja(id).map(HojaEmbalaje::getTemporadaId).orElseThrow());

            MockHttpSession otra = new MockHttpSession();
            mvc.perform(post("/info-embalaje/importar").param("id", String.valueOf(id)).session(otra))
                    .andExpect(redirectedUrl("/info-embalaje/formulario"));
            String html = mvc.perform(get("/info-embalaje/formulario").session(otra))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertTrue(html.contains("value=\"NOTA-IMPORT-77\""), "la nota de la hoja importada");
            assertTrue(html.contains("value=\"BOLSA TELA BEIGE 40x50\""), "la bolsa de tela");
            assertTrue(html.contains("value=\"4100132916\""), "sus destinaciones, no las del pedido");
            // El pedido de la temporada se ha vuelto a cargar: hay más artículos que el importado.
            assertTrue(html.contains("PXCDS-F67008") || html.split("<option").length > 5,
                    "el desplegable trae el pedido entero");
            assertTrue(html.matches("(?s).*<option[^>]*selected[^>]*>PXCBC-F67008 · LZZ.*"),
                    "el artículo importado, preseleccionado");
        } finally {
            temporadas.borrar(temporada.getId());
        }
    }

    @Test
    void importarUnaHojaSinTemporadaAbreElFormularioConSuArticulo() throws Exception {
        DatosHojaEmbalaje datos = new DatosHojaEmbalaje("APC", "A.P.C.", "Fall 26", "FEZ", 99,
                "nota huérfana", LocalDate.of(2026, 1, 15), "PXTEST-H99999", "CAW", "CAMEL", "SAC PRUEBA",
                "H99999", "60x40x30", 6, "BOLSA SIN TEMPORADA", "", "",
                List.of(new DestinacionHoja("JAPAN", "4100999999", 12)), "", "", "");
        Long id = archivo.guardar(datos, 999999L).getId();

        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(post("/info-embalaje/importar").param("id", String.valueOf(id)).session(sesion))
                .andExpect(redirectedUrl("/info-embalaje/formulario"));

        mvc.perform(get("/info-embalaje/formulario").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PXTEST-H99999")))
                .andExpect(content().string(containsString("value=\"BOLSA SIN TEMPORADA\"")))
                .andExpect(content().string(containsString("4100999999")))
                .andExpect(content().string(not(containsString("class=\"alerta error\""))));
    }

    @Test
    void elSiguienteNumeroDeEnvioSubeTrasGenerar() throws Exception {
        MockHttpSession sesion = cargarApc();
        mvc.perform(generar(sesion, "contador")).andExpect(status().isOk());

        assertTrue(archivo.siguienteNumeroEnvio() >= 271);
        mvc.perform(get("/info-embalaje/formulario").session(sesion))
                .andExpect(content().string(containsString(
                        "value=\"" + archivo.siguienteNumeroEnvio() + "\"")));
    }

    @Test
    void descargarUnaAntiguaRegeneraElWord() throws Exception {
        MockHttpSession sesion = cargarApc();
        Long id = idGenerado(mvc.perform(generar(sesion, "para regenerar"))
                .andExpect(status().isOk()).andReturn().getResponse());

        byte[] word = mvc.perform(get("/info-embalaje/antiguos/" + id + "/docx"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(TIPO_DOCX))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word))) {
            assertTrue(doc.getTables().size() > 0);
        }

        mvc.perform(get("/info-embalaje/antiguos/987654321/docx")).andExpect(status().isNotFound());
    }

    @Test
    void unaHojaQueNoExisteNoRompeLaPantallaDeAntiguos() throws Exception {
        // Un enlace viejo (la hoja se borró de la base de datos) no tiene que
        // dejar la pantalla en blanco: se dice y se puede elegir otra.
        mvc.perform(get("/info-embalaje/antiguos").param("id", "987654321"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ya no existe")));
    }
}

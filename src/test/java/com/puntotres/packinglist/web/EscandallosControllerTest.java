package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.seguridad.Seccion;
import com.puntotres.packinglist.seguridad.Usuario;
import com.puntotres.packinglist.seguridad.UsuarioRepository;

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

    @Autowired
    private UsuarioRepository usuarios;

    /**
     * La identidad con la que corren los tests de esta pantalla. Se da de alta
     * con TODAS las secciones concedibles porque el interceptor lee los
     * permisos de la base de datos, no del token, y lo que estos tests
     * comprueban es el flujo de la pantalla, no los permisos: de eso se ocupa
     * InterceptorPermisosTest, que los concede de uno en uno.
     */
    private OidcLoginRequestPostProcessor operario() {
        usuarios.findByOidEntra("oid-test").orElseGet(() -> {
            Usuario usuario = new Usuario("oid-test", "test@puntotres.com", "Operario de prueba");
            for (Seccion seccion : Seccion.concedibles()) {
                usuario.conceder(seccion);
            }
            return usuarios.saveAndFlush(usuario);
        });
        return oidcLogin().idToken(token -> token
                .claim("oid", "oid-test")
                .claim("name", "Operario de prueba")
                .claim("email", "test@puntotres.com"));
    }

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
        mvc.perform(get("/clickup").with(operario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escandallos para ClickUp")));
    }

    @Test
    void elMenuEnlazaLaPantallaDeEscandallos() throws Exception {
        mvc.perform(get("/menu").with(operario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/clickup")));
    }

    @Test
    void sinAvisosSeDescargaElExcelDirectamente() throws Exception {
        byte[] excel = mvc.perform(multipart("/clickup/procesar")
                        .file(escandalloReal("ULL770 NOIR.xlsx"))
                        .file(escandalloReal("ULL770 SAND.xlsx")).with(operario()).with(csrf()))
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
        mvc.perform(multipart("/clickup/procesar")
                        .file(escandalloReal("ULL770 NOIR.xlsx"))
                        .file(basura("roto.xlsx")).with(operario()).with(csrf())
                        .session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("roto.xlsx")))
                .andExpect(content().string(containsString("ULL770.AL245 NOIR")))
                .andExpect(content().string(containsString("/clickup/descargar")));
    }

    @Test
    void elExcelConAvisosSePuedeDescargarDespuesDesdeLaSesion() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(multipart("/clickup/procesar")
                        .file(escandalloReal("ULL770 NOIR.xlsx"))
                        .file(basura("roto.xlsx")).with(operario()).with(csrf())
                        .session(sesion))
                .andExpect(status().isOk());

        byte[] excel = mvc.perform(get("/clickup/descargar").with(operario()).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals(1, libro.getNumberOfSheets());
        }
    }

    @Test
    void siNingunFicheroEsUtilizableSeQuedaEnLaPantallaConElError() throws Exception {
        mvc.perform(multipart("/clickup/procesar").file(basura("roto.xlsx")).with(operario()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("roto.xlsx")))
                .andExpect(content().string(containsString("No se ha podido procesar")));
    }

    @Test
    void sinFicherosSeAvisaEnVezDeGenerarUnExcelVacio() throws Exception {
        mvc.perform(multipart("/clickup/procesar").with(operario()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ningún excel de escandallo")));
    }

    @Test
    void descargarSinNadaEnSesionVuelveALaPantalla() throws Exception {
        mvc.perform(get("/clickup/descargar").with(operario()).session(new MockHttpSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clickup"));
    }
}

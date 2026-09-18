package com.puntotres.packinglist.shipping.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RutasEnviosTest {

    @Autowired private MockMvc mvc;

    @Test
    void elMenuEnlazaLaSeccionDeEnvios() throws Exception {
        mvc.perform(get("/menu")).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/envios\"")));
    }

    @Test
    void losMantenimientosSePintan() throws Exception {
        mvc.perform(get("/envios/cuentas")).andExpect(status().isOk()).andExpect(view().name("cuentas"));
        mvc.perform(get("/envios/motivos")).andExpect(status().isOk()).andExpect(view().name("motivos"))
                .andExpect(content().string(containsString("Muestras")));
        mvc.perform(get("/envios/articulos")).andExpect(status().isOk()).andExpect(view().name("articulos"));
    }

    @Test
    void unaCuentaSinUsosVuelveConMensajeDeError() throws Exception {
        mvc.perform(post("/envios/cuentas").param("numero", "111111111").param("titular", "X"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("mensajeError"));
    }

    @Test
    void unaCuentaCompletaSeGuardaYAparece() throws Exception {
        mvc.perform(post("/envios/cuentas").param("numero", "111222333").param("titular", "CUENTA DE PRUEBA").param("roles", "PAYER", "DUTIES"))
                .andExpect(flash().attributeExists("mensaje"));
        mvc.perform(get("/envios/cuentas")).andExpect(content().string(containsString("111222333")));
    }

    @Test
    void laLibretaSePintaYUnContactoSeDaDeAltaYSeBusca() throws Exception {
        mvc.perform(get("/envios/contactos")).andExpect(status().isOk()).andExpect(view().name("contactos"));
        mvc.perform(get("/envios/contactos/nuevo")).andExpect(status().isOk()).andExpect(view().name("contacto"));
        mvc.perform(post("/envios/contactos").param("empresa", "TALLER PRUEBA").param("direccion1", "Zone franche")
                        .param("ciudad", "Tanger").param("pais", "MA").param("telefono", "+212500000000"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/envios/contactos"));
        mvc.perform(get("/envios/contactos").param("q", "tanger"))
                .andExpect(content().string(containsString("TALLER PRUEBA")));
    }

    @Test
    void laImportacionDeLaLibretaPrevisualizaYConfirma() throws Exception {
        byte[] csv = new org.springframework.core.io.ClassPathResource("ejemplos/envios/libreta-mydhl-recorte.csv").getInputStream().readAllBytes();
        org.springframework.mock.web.MockMultipartFile fichero = new org.springframework.mock.web.MockMultipartFile("fichero", "libreta.csv", "text/csv", csv);
        org.springframework.mock.web.MockHttpSession sesion = new org.springframework.mock.web.MockHttpSession();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/envios/contactos/importar").file(fichero).session(sesion))
                .andExpect(redirectedUrl("/envios/contactos/importar"));
        mvc.perform(get("/envios/contactos/importar").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("MAISON CLIENTE")))
                .andExpect(content().string(containsString("Con errores (2)")));
        mvc.perform(post("/envios/contactos/importar/confirmar").session(sesion))
                .andExpect(redirectedUrl("/envios/contactos")).andExpect(flash().attributeExists("errores"));
        mvc.perform(get("/envios/contactos").param("q", "PELLI ESEMPIO")).andExpect(content().string(containsString("PELLI ESEMPIO SRL")));
        mvc.perform(get("/envios/cuentas")).andExpect(content().string(containsString("956483537")));
    }

    @Test
    void unContactoQueNoExisteVuelveALaLibretaConMensaje() throws Exception {
        mvc.perform(get("/envios/contactos/999999")).andExpect(redirectedUrl("/envios/contactos")).andExpect(flash().attributeExists("mensajeError"));
        mvc.perform(post("/envios/contactos/999999/comprobar").param("tipo", "ENTREGA")).andExpect(redirectedUrl("/envios/contactos")).andExpect(flash().attributeExists("mensajeError"));
    }

    @Test
    void laImportacionDeArticulosPrevisualizaYConfirma() throws Exception {
        byte[] csv = new org.springframework.core.io.ClassPathResource("ejemplos/envios/articulos.csv").getInputStream().readAllBytes();
        org.springframework.mock.web.MockMultipartFile fichero = new org.springframework.mock.web.MockMultipartFile("fichero", "articulos.csv", "text/csv", csv);
        org.springframework.mock.web.MockHttpSession sesion = new org.springframework.mock.web.MockHttpSession();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/envios/articulos/importar").file(fichero).session(sesion))
                .andExpect(redirectedUrl("/envios/articulos/importar"));
        mvc.perform(get("/envios/articulos/importar").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Con errores (2)")))
                .andExpect(content().string(containsString("UBL029")));
        mvc.perform(post("/envios/articulos/importar/confirmar").session(sesion))
                .andExpect(redirectedUrl("/envios/articulos"));
        mvc.perform(get("/envios/articulos")).andExpect(content().string(containsString("PXCEI-F67043")));
    }

    @Test
    void elListadoSePintaYNuevoCreaUnBorradorYRedirigeASuFicha() throws Exception {
        mvc.perform(get("/envios")).andExpect(status().isOk()).andExpect(view().name("envios"));
        String destino = mvc.perform(post("/envios/nuevo")).andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        org.junit.jupiter.api.Assertions.assertTrue(destino.matches("/envios/\\d+"), destino);
        mvc.perform(get(destino)).andExpect(status().isOk()).andExpect(view().name("envio"))
                .andExpect(content().string(containsString("DRAFT")))
                .andExpect(content().string(containsString("Tarifar")));
    }

    @Test
    void anadirUnBultoYGuardarSeReflejaEnLaFicha() throws Exception {
        String destino = mvc.perform(post("/envios/nuevo")).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(post(destino).param("accion", "anadirBulto").param("responsable", "Jordi"))
                .andExpect(redirectedUrl(destino));
        mvc.perform(post(destino).param("accion", "guardar").param("responsable", "Jordi")
                        .param("bultos[0].peso", "2.5").param("bultos[0].largo", "40").param("bultos[0].ancho", "30").param("bultos[0].alto", "20"))
                .andExpect(flash().attribute("mensaje", "Guardado."));
        mvc.perform(get(destino)).andExpect(content().string(containsString("value=\"2.5\"")));
    }

    @Test
    void tarifarSinDatosVuelveConLaListaDeProblemas() throws Exception {
        String destino = mvc.perform(post("/envios/nuevo")).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(post(destino).param("accion", "tarifar").param("responsable", "Jordi"))
                .andExpect(flash().attributeExists("problemas"));
    }

    @Test
    void unEnvioQueNoExisteVuelveAlListadoConMensaje() throws Exception {
        mvc.perform(get("/envios/999999")).andExpect(redirectedUrl("/envios")).andExpect(flash().attributeExists("mensajeError"));
    }
}

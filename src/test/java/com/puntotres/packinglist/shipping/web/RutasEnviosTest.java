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
}

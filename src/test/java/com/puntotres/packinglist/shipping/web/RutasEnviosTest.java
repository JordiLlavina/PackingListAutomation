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
}

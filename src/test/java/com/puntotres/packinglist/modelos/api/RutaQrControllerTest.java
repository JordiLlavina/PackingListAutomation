package com.puntotres.packinglist.modelos.api;

import static com.puntotres.packinglist.modelos.ComoUsuario.usuarioDePrueba;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.Modelos;
import com.puntotres.packinglist.modelos.service.Usuarios;
import com.puntotres.packinglist.persistence.Modelo;

/**
 * La dirección del QR de un modelo: se abre sin haber entrado (la SPA pide el
 * login si hace falta) y lleva a la ficha del modelo en la app.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RutaQrControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Modelos modelos;

    @Autowired
    private Usuarios usuarios;

    @Test
    void elQrLlevaALaFichaDelModeloSinHaberEntrado() throws Exception {
        Usuario despacho = usuarioDePrueba(usuarios, Rol.DESPACHO);
        Modelo modelo = modelos.darDeAlta(new Modelos.AltaManual("AMI", null, "T9-QR-RUTA", null, null, null,
                null), new UsuarioActual(despacho.getId(), despacho.getNombre(), despacho.getRol(), null));

        mvc.perform(get("/m/" + modelo.getId()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/app/modelos/" + modelo.getId()));
    }

    @Test
    void unModeloQueNoExisteLlevaIgualmenteASuId() throws Exception {
        mvc.perform(get("/m/987654322"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/app/modelos/987654322"));
    }
}

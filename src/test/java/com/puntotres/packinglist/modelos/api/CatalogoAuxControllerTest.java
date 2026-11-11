package com.puntotres.packinglist.modelos.api;

import static com.puntotres.packinglist.modelos.ComoUsuario.comoUsuario;
import static com.puntotres.packinglist.modelos.ComoUsuario.usuarioDePrueba;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * Clientes, temporadas, tipos de cambio y destinos de aplicación. La base de
 * datos es la misma para toda la suite: las temporadas de este test llevan el
 * prefijo {@code T7-}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogoAuxControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Usuarios usuarios;

    @Test
    void losClientesSalenDelYmlEnSuOrden() throws Exception {
        mvc.perform(get("/api/clientes").with(comoUsuario(usuarioDePrueba(usuarios, Rol.TALLER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clave").value("AMI"))
                .andExpect(jsonPath("$[0].nombre").value("AMI"))
                .andExpect(jsonPath("$[?(@.clave=='APC')]").exists());
    }

    @Test
    void crearTemporadaSoloConNombre() throws Exception {
        mvc.perform(crear("AMI", "T7-TEMP"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.temporada").value("T7-TEMP"));

        mvc.perform(get("/api/temporadas").param("cliente", "AMI")
                        .with(comoUsuario(usuarioDePrueba(usuarios, Rol.TALLER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.temporada=='T7-TEMP')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.temporada=='T7-TEMP')].id").isNotEmpty());
    }

    @Test
    void repetidaDa400() throws Exception {
        mvc.perform(crear("AMI", "T7-REPE")).andExpect(status().isCreated());

        mvc.perform(crear("AMI", "t7-repe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Ya existe la temporada t7-repe de AMI"));
    }

    @Test
    void sinClienteOSinNombreDa400() throws Exception {
        mvc.perform(crear("AMI", "  ")).andExpect(status().isBadRequest());
        mvc.perform(crear("", "T7-VACIA")).andExpect(status().isBadRequest());
        mvc.perform(crear("NO-EXISTE-T7", "T7-VACIA")).andExpect(status().isBadRequest());
    }

    @Test
    void tallerLeeLosTiposDeCambioPeroNoCreaTemporadas() throws Exception {
        mvc.perform(get("/api/tipos-cambio").with(comoUsuario(usuarioDePrueba(usuarios, Rol.TALLER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].clave").value("MEDIDAS_PATRONES"))
                .andExpect(jsonPath("$[0].texto").value("Medidas / Patrones"))
                .andExpect(jsonPath("$[0].icono").value("medidas"))
                .andExpect(jsonPath("$[3].clave").value("CONSUMO_PIEL"))
                .andExpect(jsonPath("$[7].texto").value("Otros"));

        mvc.perform(post("/api/temporadas").with(comoUsuario(usuarioDePrueba(usuarios, Rol.TALLER))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"AMI\",\"temporada\":\"T7-TALLER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void losDestinosDeAplicacionTienenTexto() throws Exception {
        mvc.perform(get("/api/destinos-aplicacion").with(comoUsuario(usuarioDePrueba(usuarios, Rol.DESPACHO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].clave").value("ICSUITE"))
                .andExpect(jsonPath("$[0].texto").value("ICSUITE"))
                .andExpect(jsonPath("$[1].clave").value("EXCEL_ESCANDALLOS"))
                .andExpect(jsonPath("$[1].texto").value("Excel de escandallos"));
    }

    private MockHttpServletRequestBuilder crear(String cliente, String temporada) {
        return post("/api/temporadas").with(comoUsuario(usuarioDePrueba(usuarios, Rol.DESPACHO))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cliente\":\"" + cliente + "\",\"temporada\":\"" + temporada + "\"}");
    }
}

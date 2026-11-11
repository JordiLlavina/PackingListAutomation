package com.puntotres.packinglist.modelos.seguridad;

import static com.puntotres.packinglist.modelos.ComoUsuario.comoUsuario;
import static com.puntotres.packinglist.modelos.ComoUsuario.usuarioDePrueba;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * La SPA lee el token CSRF de la cookie {@code XSRF-TOKEN} y lo devuelve en la
 * cabecera {@code X-XSRF-TOKEN}.
 *
 * Va en su propio contexto de Spring (la propiedad de abajo no la usa nadie
 * más, así que el contexto no se comparte) y sin el {@code csrf()} de
 * spring-security-test, a propósito: la primera vez que un test usa
 * {@code csrf()}, spring-security-test cambia para siempre el repositorio de
 * tokens del {@code CsrfFilter} de ese contexto por uno que guarda el token
 * en la sesión, y desde entonces ninguna respuesta lleva la cookie. En el
 * contexto compartido este test dependería del orden en que corre la suite.
 */
@SpringBootTest(properties = "prueba.contexto-propio=cookie-xsrf")
@AutoConfigureMockMvc
class CookieXsrfTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Usuarios usuarios;

    @Test
    void laApiDejaLaCookieXsrfParaLaSpa() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/sesion")).andReturn();
        Cookie xsrf = resultado.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrf, "la SPA lee el token de la cookie XSRF-TOKEN");
        assertFalse(xsrf.isHttpOnly(), "la SPA tiene que poder leerla");

        // Con el valor de la cookie en X-XSRF-TOKEN el DELETE pasa el CSRF; sin él, no.
        mvc.perform(delete("/api/sesion").with(comoUsuario(usuarioDePrueba(usuarios, Rol.DESPACHO)))
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/sesion").with(comoUsuario(usuarioDePrueba(usuarios, Rol.DESPACHO)))
                        .cookie(xsrf).header("X-XSRF-TOKEN", "otro"))
                .andExpect(status().isForbidden());
    }
}

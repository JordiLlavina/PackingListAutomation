package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.seguridad.Seccion;
import com.puntotres.packinglist.seguridad.Usuario;
import com.puntotres.packinglist.seguridad.UsuarioRepository;
import com.puntotres.packinglist.persistence.CatalogoTarasJpa;

/**
 * Pesar un cartón tiene que poder hacerse desde la pantalla. Sin esto, mover
 * las taras a la base de datos habría empeorado las cosas: de editar un yml a
 * escribir SQL a mano.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TarasControllerTest {

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

    @Autowired
    private CatalogoTarasJpa catalogo;

    @Test
    void laPantallaListaLasTarasConocidas() throws Exception {
        catalogo.guardar("77x77x77", 2.0);

        mvc.perform(get("/taras").with(operario()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("taras"))
                .andExpect(content().string(containsString("77x77x77")));
    }

    @Test
    void guardarUnaTaraNuevaLaDejaEnElCatalogo() throws Exception {
        mvc.perform(post("/taras").with(operario()).with(csrf()).param("medida", "50x30x20").param("taraKg", "0.9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/taras"));

        assertEquals(0.9, catalogo.taraPara("50x30x20").orElseThrow());
    }

    @Test
    void corregirElPesoDeUnCartonYaConocidoLoActualiza() throws Exception {
        catalogo.guardar("55x35x25", 1.0);

        mvc.perform(post("/taras").with(operario()).with(csrf()).param("medida", "55x35x25").param("taraKg", "1.4"));

        assertEquals(1.4, catalogo.taraPara("55x35x25").orElseThrow());
    }

    @Test
    void unaMedidaIlegibleNoEntraEnElCatalogo() throws Exception {
        mvc.perform(post("/taras").with(operario()).with(csrf()).param("medida", "grande").param("taraKg", "1.0"))
                .andExpect(redirectedUrl("/taras"));

        assertTrue(catalogo.taraPara("grande").isEmpty(),
                "una medida que no es LxAnchoxAlto no sirve para calcular volumen ni altura");
    }

    @Test
    void unPesoNegativoNoEntraEnElCatalogo() throws Exception {
        mvc.perform(post("/taras").with(operario()).with(csrf()).param("medida", "44x33x22").param("taraKg", "-1"));

        assertTrue(catalogo.taraPara("44x33x22").isEmpty());
    }
}

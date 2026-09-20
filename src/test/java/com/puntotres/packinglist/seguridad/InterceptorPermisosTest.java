package com.puntotres.packinglist.seguridad;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Los permisos se leen de la base de datos en cada petición, no de la sesión:
 * quitarle una sección a alguien que está dentro surte efecto en su siguiente
 * clic.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InterceptorPermisosTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository repositorio;

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
    }

    private OidcLoginRequestPostProcessor identidadDe(Usuario usuario) {
        return oidcLogin().idToken(token -> token
                .claim("oid", usuario.getOidEntra())
                .claim("name", usuario.getNombre())
                .claim("email", usuario.getCorreo()));
    }

    private Usuario guardar(Usuario usuario) {
        return repositorio.saveAndFlush(usuario);
    }

    @Test
    void sinLaSeccionConcedidaLaPantallaNoSeAbre() throws Exception {
        Usuario ana = guardar(new Usuario("oid-1", "ana@puntotres.com", "Ana"));

        mockMvc.perform(get("/taras").with(identidadDe(ana)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/sin-permiso?seccion=TARAS"));
    }

    @Test
    void conLaSeccionConcedidaLaPantallaSeAbre() throws Exception {
        Usuario ana = new Usuario("oid-1", "ana@puntotres.com", "Ana");
        ana.conceder(Seccion.TARAS);
        guardar(ana);

        mockMvc.perform(get("/taras").with(identidadDe(ana)))
                .andExpect(status().isOk());
    }

    @Test
    void elMenuSeAbreAunqueNoHayaNingunPermiso() throws Exception {
        Usuario ana = guardar(new Usuario("oid-1", "ana@puntotres.com", "Ana"));

        // Quien entra por primera vez tiene que ver algo: el menú vacío y a
        // quién pedir permisos. Un 403 en blanco no dice qué hacer.
        mockMvc.perform(get("/menu").with(identidadDe(ana)))
                .andExpect(status().isOk());
    }

    @Test
    void unPermisoRetiradoEnCalienteSurteEfectoEnElSiguienteClic() throws Exception {
        Usuario ana = new Usuario("oid-1", "ana@puntotres.com", "Ana");
        ana.conceder(Seccion.TARAS);
        guardar(ana);

        mockMvc.perform(get("/taras").with(identidadDe(ana)))
                .andExpect(status().isOk());

        Usuario releida = repositorio.findByOidEntra("oid-1").orElseThrow();
        releida.retirar(Seccion.TARAS);
        repositorio.saveAndFlush(releida);

        mockMvc.perform(get("/taras").with(identidadDe(ana)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @org.junit.jupiter.api.Disabled("Lo habilita la tarea 4.5")
    void administracionSoloLaAbreUnAdministrador() throws Exception {
        Usuario ana = guardar(new Usuario("oid-1", "ana@puntotres.com", "Ana"));
        Usuario jefa = new Usuario("oid-2", "jefa@puntotres.com", "Jefa");
        jefa.setAdministrador(true);
        guardar(jefa);

        mockMvc.perform(get("/administracion/usuarios").with(identidadDe(ana)))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/administracion/usuarios").with(identidadDe(jefa)))
                .andExpect(status().isOk());
    }

    @Test
    void unUsuarioDesactivadoNoAbreNada() throws Exception {
        Usuario ana = new Usuario("oid-1", "ana@puntotres.com", "Ana");
        ana.conceder(Seccion.TARAS);
        ana.setActivo(false);
        guardar(ana);

        mockMvc.perform(get("/taras").with(identidadDe(ana)))
                .andExpect(status().is3xxRedirection());
    }
}

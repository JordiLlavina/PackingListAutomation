package com.puntotres.packinglist.modelos.seguridad;

import static com.puntotres.packinglist.modelos.ComoUsuario.comoUsuario;
import static com.puntotres.packinglist.modelos.ComoUsuario.usuarioDePrueba;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.puntotres.packinglist.modelos.persistence.DispositivoRepository;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.service.Dispositivos;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * La seguridad solo vale para la app de modelos ({@code /app}, {@code /api},
 * {@code /m}); el resto del programa sigue abierto como siempre. Cada test usa
 * sus propios logins ({@code t6-...}): la base de datos en memoria es la misma
 * para toda la suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeguridadTest {

    private static final String HACE_FALTA_ENTRAR = "{\"mensaje\":\"Hace falta entrar\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private Dispositivos dispositivos;

    @Autowired
    private DispositivoRepository repositorioDispositivos;

    @Test
    void lasRutasViejasSiguenAbiertasSinSesion() throws Exception {
        for (String ruta : List.of("/menu", "/catalogo", "/taras")) {
            mvc.perform(get(ruta)).andExpect(status().isOk());
        }
        // Los fetch de las pantallas viejas no mandan token CSRF y tienen que seguir funcionando.
        mvc.perform(post("/colores/colores/fila").param("cliente", "LGN").param("codigo", "T6X").param("valor", ""))
                .andExpect(status().isOk());
    }

    @Test
    void laApiSinSesionDa401ConMensajeYNoRedirige() throws Exception {
        for (String ruta : List.of("/api/sesion", "/api/lo-que-sea")) {
            mvc.perform(get(ruta))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(content().json(HACE_FALTA_ENTRAR, true));
        }
        mvc.perform(delete("/api/sesion").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().json(HACE_FALTA_ENTRAR, true));
    }

    @Test
    void unaPeticionConCookieDeDispositivoValidaEntraComoTaller() throws Exception {
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller Tánger T6", "t6-cookie", "", Rol.TALLER));
        String codigo = dispositivos.generarCodigoAlta(taller.getId());

        MvcResult canje = mvc.perform(post("/api/sesion/dispositivo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigo + "\",\"nombre\":\"Tablet T6\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = canje.getResponse().getCookie(FiltroDispositivo.COOKIE);
        assertNotNull(cookie);

        mvc.perform(get("/api/sesion").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taller.getId()))
                .andExpect(jsonPath("$.nombre").value("Taller Tánger T6"))
                .andExpect(jsonPath("$.rol").value("TALLER"))
                .andExpect(jsonPath("$.dispositivo").value(true));

        assertNotNull(dispositivos.porToken(cookie.getValue()).orElseThrow().getUltimoUso(),
                "entrar con el dispositivo apunta su último uso");
    }

    @Test
    void unDispositivoRevocadoRecibe401() throws Exception {
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller Fez T6", "t6-revocado", "", Rol.TALLER));
        Dispositivos.TokenDispositivo token = dispositivos.canjear(
                dispositivos.generarCodigoAlta(taller.getId()), "Móvil T6");
        Cookie cookie = new Cookie(FiltroDispositivo.COOKIE, token.tokenEnClaro());
        mvc.perform(get("/api/sesion").cookie(cookie)).andExpect(status().isOk());

        dispositivos.revocar(token.dispositivo().getId());

        mvc.perform(get("/api/sesion").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(HACE_FALTA_ENTRAR, true));
    }

    @Test
    void unUsuarioDesactivadoNoEntraConSuDispositivo() throws Exception {
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller Ubrique T6", "t6-inactivo", "", Rol.TALLER));
        Dispositivos.TokenDispositivo token = dispositivos.canjear(
                dispositivos.generarCodigoAlta(taller.getId()), "Móvil T6");
        Cookie cookie = new Cookie(FiltroDispositivo.COOKIE, token.tokenEnClaro());

        usuarios.cambiar(taller.getId(), null, null, false, null);

        mvc.perform(get("/api/sesion").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(HACE_FALTA_ENTRAR, true));
    }

    @Test
    void elUsoDelDispositivoSeApuntaComoMuchoUnaVezPorHora() throws Exception {
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller T6 uso", "t6-uso", "", Rol.TALLER));
        Dispositivos.TokenDispositivo token = dispositivos.canjear(
                dispositivos.generarCodigoAlta(taller.getId()), "Móvil T6");
        Cookie cookie = new Cookie(FiltroDispositivo.COOKIE, token.tokenEnClaro());

        mvc.perform(get("/api/sesion").cookie(cookie)).andExpect(status().isOk());
        LocalDateTime primero = ultimoUso(token);
        mvc.perform(get("/api/sesion").cookie(cookie)).andExpect(status().isOk());
        LocalDateTime segundo = ultimoUso(token);

        assertNotNull(primero);
        assertEquals(primero, segundo, "la segunda petición de la misma hora no vuelve a escribir");
    }

    @Test
    void tallerNoPuedeLlamarAUnMetodoDeDespacho() throws Exception {
        // Humo: el 403 de un método de despacho se ancla en la Task 7, con /api/usuarios.
        Usuario taller = usuarioDePrueba(usuarios, Rol.TALLER);
        mvc.perform(get("/api/sesion").with(comoUsuario(taller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("TALLER"))
                .andExpect(jsonPath("$.dispositivo").value(false));
    }

    @Test
    void elAdminTieneTambienElPapelDeDespacho() {
        assertEquals(Set.of("ROLE_TALLER"), papeles(usuarioDePrueba(usuarios, Rol.TALLER)));
        assertEquals(Set.of("ROLE_DESPACHO"), papeles(usuarioDePrueba(usuarios, Rol.DESPACHO)));
        assertEquals(Set.of("ROLE_ADMIN", "ROLE_DESPACHO"), papeles(usuarioDePrueba(usuarios, Rol.ADMIN)));

        AutenticacionModelos autenticacion = AutenticacionModelos.de(usuarioDePrueba(usuarios, Rol.ADMIN), null);
        assertTrue(autenticacion.isAuthenticated());
        assertTrue(autenticacion.getPrincipal().esDespacho());
        assertNull(autenticacion.getCredentials());
    }

    @Test
    void unDeleteDeLaApiSinTokenCsrfDa403EnJson() throws Exception {
        Usuario despacho = usuarioDePrueba(usuarios, Rol.DESPACHO);
        mvc.perform(delete("/api/sesion").with(comoUsuario(despacho)))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().json("{\"mensaje\":\"No tienes permiso para esto\"}", true));
        mvc.perform(delete("/api/sesion").with(comoUsuario(despacho)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private LocalDateTime ultimoUso(Dispositivos.TokenDispositivo token) {
        return repositorioDispositivos.findById(token.dispositivo().getId()).orElseThrow().getUltimoUso();
    }

    private static Set<String> papeles(Usuario usuario) {
        return AutenticacionModelos.de(usuario, null).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}

package com.puntotres.packinglist.modelos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.persistence.UsuarioRepository;
import com.puntotres.packinglist.modelos.seguridad.FiltroDispositivo;
import com.puntotres.packinglist.modelos.service.Dispositivos;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * Entrar con contraseña (el despacho con sesión, el taller dando de alta el
 * aparato) y salir. Cada test usa sus propios logins ({@code t6-...}) y, los
 * que fallan a propósito, su propia IP: la base de datos y el contador de
 * intentos son los mismos para toda la suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SesionControllerTest {

    private static final String INCORRECTOS = "{\"mensaje\":\"Usuario o contraseña incorrectos\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private Dispositivos dispositivos;

    @Autowired
    private UsuarioRepository repositorioUsuarios;

    @Test
    void elLoginBuenoAbreUnaSesionDeOchoHorasYGetDiceElRol() throws Exception {
        Usuario despacho = usuarios.crear(new Usuarios.Alta("Despacho T6", "t6-despacho", "secreto", Rol.DESPACHO));

        MvcResult login = mvc.perform(login("T6-Despacho", "secreto"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":" + despacho.getId()
                        + ",\"nombre\":\"Despacho T6\",\"rol\":\"DESPACHO\",\"dispositivo\":false}", true))
                .andReturn();
        assertTrue(login.getResponse().getHeaders("Set-Cookie").stream()
                        .noneMatch(cabecera -> cabecera.startsWith(FiltroDispositivo.COOKIE + "=")),
                "el despacho no da de alta el aparato");
        MockHttpSession sesion = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(sesion);
        assertEquals(8 * 3600, sesion.getMaxInactiveInterval());

        mvc.perform(get("/api/sesion").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":" + despacho.getId()
                        + ",\"nombre\":\"Despacho T6\",\"rol\":\"DESPACHO\",\"dispositivo\":false}", true));
    }

    @Test
    void elLoginMaloDa401ConMensaje() throws Exception {
        Usuario baja = usuarios.crear(new Usuarios.Alta("Baja T6", "t6-baja", "secreto", Rol.DESPACHO));
        usuarios.cambiar(baja.getId(), null, null, false, null);
        usuarios.crear(new Usuarios.Alta("Admin T6", "t6-admin", "secreto", Rol.ADMIN));

        String ip = "10.6.0.3";
        mvc.perform(login("t6-admin", "otra").with(desde(ip)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INCORRECTOS, true));
        mvc.perform(login("t6-nadie", "secreto").with(desde(ip)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INCORRECTOS, true));
        mvc.perform(login("t6-baja", "secreto").with(desde(ip)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INCORRECTOS, true));
    }

    @Test
    void elTallerEntraConContrasenaYSuAparatoSeQuedaDadoDeAltaUnAno() throws Exception {
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller T6 canje", "t6-canje", "clave-taller", Rol.TALLER));

        MvcResult entrada = mvc.perform(login("t6-canje", "clave-taller", "Tablet del taller"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":" + taller.getId()
                        + ",\"nombre\":\"Taller T6 canje\",\"rol\":\"TALLER\",\"dispositivo\":true}", true))
                .andReturn();

        String setCookie = setCookieDe(entrada, FiltroDispositivo.COOKIE);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("SameSite=Lax"), setCookie);
        assertTrue(setCookie.contains("Max-Age=31536000"), setCookie);
        assertTrue(setCookie.contains("Path=/"), setCookie);
        assertFalse(setCookie.contains("Secure"), "en http no se marca Secure: " + setCookie);
        Cookie cookie = entrada.getResponse().getCookie(FiltroDispositivo.COOKIE);
        assertEquals("Tablet del taller", dispositivos.porToken(cookie.getValue()).orElseThrow().getNombre());

        // Con la cookie entra sin sesión.
        mvc.perform(get("/api/sesion").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("TALLER"))
                .andExpect(jsonPath("$.dispositivo").value(true));
    }

    @Test
    void elTallerPuedeEntrarConLaTabletYConElMovilALaVez() throws Exception {
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller T6 dos", "t6-dos", "clave-taller", Rol.TALLER));

        Cookie tablet = mvc.perform(login("t6-dos", "clave-taller", "Tablet")).andReturn()
                .getResponse().getCookie(FiltroDispositivo.COOKIE);
        Cookie movil = mvc.perform(login("t6-dos", "clave-taller", "Móvil")).andReturn()
                .getResponse().getCookie(FiltroDispositivo.COOKIE);

        assertNotEquals(tablet.getValue(), movil.getValue());
        mvc.perform(get("/api/sesion").cookie(tablet)).andExpect(jsonPath("$.id").value(taller.getId()));
        mvc.perform(get("/api/sesion").cookie(movil)).andExpect(jsonPath("$.id").value(taller.getId()));
    }

    @Test
    void elTallerQueEntraDondeHabiaUnaSesionDeDespachoNoSeQuedaComoDespacho() throws Exception {
        usuarios.crear(new Usuarios.Alta("Despacho T6 antes", "t6-antes", "secreto", Rol.DESPACHO));
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller T6 despues", "t6-despues", "clave-taller", Rol.TALLER));
        MockHttpSession sesion = (MockHttpSession) mvc.perform(login("t6-antes", "secreto"))
                .andReturn().getRequest().getSession(false);

        Cookie cookie = mvc.perform(login("t6-despues", "clave-taller", "Tablet").session(sesion)).andReturn()
                .getResponse().getCookie(FiltroDispositivo.COOKIE);

        mvc.perform(get("/api/sesion").session(sesion).cookie(cookie))
                .andExpect(jsonPath("$.id").value(taller.getId()))
                .andExpect(jsonPath("$.rol").value("TALLER"));
    }

    @Test
    void unTallerSinContrasenaNoEntra() throws Exception {
        // Los dados de alta cuando el taller entraba con código no tienen contraseña.
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller T6 sin clave", "t6-sin-clave", "provisional", Rol.TALLER));
        usuarioSinContrasena(taller);
        mvc.perform(login(taller.getLogin(), "").with(desde("10.6.0.4")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INCORRECTOS, true));
    }

    @Test
    void enHttpsLaCookieDelDispositivoEsSecure() throws Exception {
        usuarios.crear(new Usuarios.Alta("Taller T6 https", "t6-https", "clave-taller", Rol.TALLER));

        MvcResult entrada = mvc.perform(login("t6-https", "clave-taller", "Móvil").secure(true))
                .andExpect(status().isOk())
                .andReturn();

        assertTrue(setCookieDe(entrada, FiltroDispositivo.COOKIE).contains("Secure"));
    }

    @Test
    void salirQuitaLaEntradaSinInvalidarLaSesionYBorraLaCookieDelDispositivo() throws Exception {
        usuarios.crear(new Usuarios.Alta("Despacho T6 salir", "t6-salir", "secreto", Rol.DESPACHO));
        MockHttpSession sesion = (MockHttpSession) mvc.perform(login("t6-salir", "secreto"))
                .andReturn().getRequest().getSession(false);

        // Lo de la app vieja vive en la misma sesión (envío en curso, corte en curso...).
        sesion.setAttribute("t6-envioEnCurso", "lo tecleado en el packing list");
        String idAntes = sesion.getId();

        MvcResult salida = mvc.perform(delete("/api/sesion").session(sesion).with(csrf())
                        .cookie(new Cookie(FiltroDispositivo.COOKIE, "lo-que-sea")))
                .andExpect(status().isNoContent())
                .andReturn();

        assertFalse(sesion.isInvalid(), "salir de /app no puede tirar la sesión de las pantallas viejas");
        assertEquals("lo tecleado en el packing list", sesion.getAttribute("t6-envioEnCurso"));
        assertNotEquals(idAntes, sesion.getId(), "el id de la sesión cambia al salir");
        String borrado = setCookieDe(salida, FiltroDispositivo.COOKIE);
        assertTrue(borrado.contains("Max-Age=0"), borrado);
        mvc.perform(get("/api/sesion").session(sesion))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void salirSinCookieDeDispositivoNoTocaCookies() throws Exception {
        usuarios.crear(new Usuarios.Alta("Despacho T6 salir2", "t6-salir2", "secreto", Rol.DESPACHO));
        MockHttpSession sesion = (MockHttpSession) mvc.perform(login("t6-salir2", "secreto"))
                .andReturn().getRequest().getSession(false);

        MvcResult salida = mvc.perform(delete("/api/sesion").session(sesion).with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn();

        assertTrue(salida.getResponse().getHeaders("Set-Cookie").stream()
                .noneMatch(cabecera -> cabecera.startsWith(FiltroDispositivo.COOKIE + "=")));
    }

    @Test
    void diezEntradasFallidasSeguidasFrenanLaIp() throws Exception {
        usuarios.crear(new Usuarios.Alta("Taller T6 freno", "t6-freno", "clave-taller", Rol.TALLER));
        String ip = "10.6.0.1";
        for (int intento = 1; intento <= 10; intento++) {
            mvc.perform(login("t6-freno", "mala").with(desde(ip)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login("t6-freno", "mala").with(desde(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().json("{\"mensaje\":\"Demasiados intentos, espera unos minutos\"}", true));

        // Frenada, ni la contraseña buena entra desde esa IP; desde otra, sí.
        mvc.perform(login("t6-freno", "clave-taller").with(desde(ip))).andExpect(status().isTooManyRequests());
        mvc.perform(login("t6-freno", "clave-taller").with(desde("10.6.0.9"))).andExpect(status().isOk());
    }

    @Test
    void unaEntradaBuenaBorraLosFallosDeSuIp() throws Exception {
        usuarios.crear(new Usuarios.Alta("Taller T6 limpia", "t6-limpia", "clave-taller", Rol.TALLER));
        String ip = "10.6.0.2";
        for (int intento = 1; intento <= 9; intento++) {
            mvc.perform(login("t6-limpia", "mala").with(desde(ip))).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("t6-limpia", "clave-taller").with(desde(ip))).andExpect(status().isOk());

        for (int intento = 1; intento <= 10; intento++) {
            mvc.perform(login("t6-limpia", "mala").with(desde(ip))).andExpect(status().isUnauthorized());
        }
    }

    private void usuarioSinContrasena(Usuario usuario) {
        repositorioUsuarios.findById(usuario.getId()).ifPresent(u -> {
            u.cambiarPassword(null);
            repositorioUsuarios.save(u);
        });
    }

    private static MockHttpServletRequestBuilder login(String login, String password) {
        return post("/api/sesion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}");
    }

    private static MockHttpServletRequestBuilder login(String login, String password, String nombre) {
        return post("/api/sesion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\",\"nombre\":\"" + nombre + "\"}");
    }

    private static RequestPostProcessor desde(String ip) {
        return peticion -> {
            peticion.setRemoteAddr(ip);
            return peticion;
        };
    }

    private static String setCookieDe(MvcResult resultado, String nombre) {
        return resultado.getResponse().getHeaders("Set-Cookie").stream()
                .filter(cabecera -> cabecera.startsWith(nombre + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hay Set-Cookie de " + nombre));
    }
}

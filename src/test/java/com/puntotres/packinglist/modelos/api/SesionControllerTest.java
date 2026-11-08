package com.puntotres.packinglist.modelos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.puntotres.packinglist.modelos.seguridad.FiltroDispositivo;
import com.puntotres.packinglist.modelos.service.Dispositivos;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * Entrar con contraseña, entrar con un código de alta de dispositivo y salir.
 * Cada test usa sus propios logins ({@code t6-...}) y, los del freno de
 * canjes, su propia IP: la base de datos y el contador de intentos son los
 * mismos para toda la suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SesionControllerTest {

    private static final String NO_VALE = "{\"mensaje\":\"El código no vale o ha caducado\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private Dispositivos dispositivos;

    @Test
    void elLoginBuenoAbreUnaSesionDeOchoHorasYGetDiceElRol() throws Exception {
        Usuario despacho = usuarios.crear(new Usuarios.Alta("Despacho T6", "t6-despacho", "secreto", Rol.DESPACHO));

        MvcResult login = mvc.perform(login("T6-Despacho", "secreto"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":" + despacho.getId()
                        + ",\"nombre\":\"Despacho T6\",\"rol\":\"DESPACHO\"}", true))
                .andReturn();
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

        String incorrectos = "{\"mensaje\":\"Usuario o contraseña incorrectos\"}";
        mvc.perform(login("t6-admin", "otra"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(incorrectos, true));
        mvc.perform(login("t6-nadie", "secreto"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(incorrectos, true));
        mvc.perform(login("t6-baja", "secreto"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(incorrectos, true));
    }

    @Test
    void elCanjeDelCodigoPoneLaCookieYLaSegundaVezDa400() throws Exception {
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller T6 canje", "t6-canje", "", Rol.TALLER));
        String codigo = dispositivos.generarCodigoAlta(taller.getId());

        MvcResult canje = mvc.perform(canje(codigo, "Tablet del taller"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":" + taller.getId()
                        + ",\"nombre\":\"Taller T6 canje\",\"rol\":\"TALLER\"}", true))
                .andReturn();

        String setCookie = setCookieDe(canje, FiltroDispositivo.COOKIE);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("SameSite=Lax"), setCookie);
        assertTrue(setCookie.contains("Max-Age=31536000"), setCookie);
        assertTrue(setCookie.contains("Path=/"), setCookie);
        assertFalse(setCookie.contains("Secure"), "en http no se marca Secure: " + setCookie);
        String token = canje.getResponse().getCookie(FiltroDispositivo.COOKIE).getValue();
        assertEquals(taller.getId(), dispositivos.porToken(token).orElseThrow().getUsuarioId());

        mvc.perform(canje(codigo, "Otro móvil"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(NO_VALE, true));
    }

    @Test
    void enHttpsLaCookieDelDispositivoEsSecure() throws Exception {
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller T6 https", "t6-https", "", Rol.TALLER));
        String codigo = dispositivos.generarCodigoAlta(taller.getId());

        MvcResult canje = mvc.perform(canje(codigo, "Móvil").secure(true))
                .andExpect(status().isOk())
                .andReturn();

        assertTrue(setCookieDe(canje, FiltroDispositivo.COOKIE).contains("Secure"));
    }

    @Test
    void salirInvalidaLaSesionYBorraLaCookieDelDispositivo() throws Exception {
        usuarios.crear(new Usuarios.Alta("Despacho T6 salir", "t6-salir", "secreto", Rol.DESPACHO));
        MockHttpSession sesion = (MockHttpSession) mvc.perform(login("t6-salir", "secreto"))
                .andReturn().getRequest().getSession(false);

        MvcResult salida = mvc.perform(delete("/api/sesion").session(sesion).with(csrf())
                        .cookie(new Cookie(FiltroDispositivo.COOKIE, "lo-que-sea")))
                .andExpect(status().isNoContent())
                .andReturn();

        assertTrue(sesion.isInvalid());
        String borrado = setCookieDe(salida, FiltroDispositivo.COOKIE);
        assertTrue(borrado.contains("Max-Age=0"), borrado);
        mvc.perform(get("/api/sesion").session(new MockHttpSession()))
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
    void onceCanjesFallidosSeguidosDanUn429() throws Exception {
        String ip = "10.6.0.1";
        for (int intento = 1; intento <= 10; intento++) {
            mvc.perform(canje("t6-no", "Móvil").with(desde(ip)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json(NO_VALE, true));
        }
        mvc.perform(canje("t6-no", "Móvil").with(desde(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().json("{\"mensaje\":\"Demasiados intentos, espera unos minutos\"}", true));

        // Frenada, ni un código bueno entra desde esa IP; desde otra, sí se sigue intentando.
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller T6 freno", "t6-freno", "", Rol.TALLER));
        String codigo = dispositivos.generarCodigoAlta(taller.getId());
        mvc.perform(canje(codigo, "Móvil").with(desde(ip))).andExpect(status().isTooManyRequests());
        mvc.perform(canje(codigo, "Móvil").with(desde("10.6.0.9"))).andExpect(status().isOk());
    }

    @Test
    void unCanjeBuenoBorraLosFallosDeSuIp() throws Exception {
        String ip = "10.6.0.2";
        for (int intento = 1; intento <= 9; intento++) {
            mvc.perform(canje("t6-no", "Móvil").with(desde(ip))).andExpect(status().isBadRequest());
        }
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller T6 limpia", "t6-limpia", "", Rol.TALLER));
        mvc.perform(canje(dispositivos.generarCodigoAlta(taller.getId()), "Móvil").with(desde(ip)))
                .andExpect(status().isOk());

        for (int intento = 1; intento <= 10; intento++) {
            mvc.perform(canje("t6-no", "Móvil").with(desde(ip))).andExpect(status().isBadRequest());
        }
    }

    private static MockHttpServletRequestBuilder login(String login, String password) {
        return post("/api/sesion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}");
    }

    private static MockHttpServletRequestBuilder canje(String codigo, String nombre) {
        return post("/api/sesion/dispositivo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"" + codigo + "\",\"nombre\":\"" + nombre + "\"}");
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

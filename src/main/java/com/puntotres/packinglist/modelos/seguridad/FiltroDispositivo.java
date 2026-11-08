package com.puntotres.packinglist.modelos.seguridad;

import java.io.IOException;
import java.util.Arrays;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.service.Dispositivos;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * Cookie {@value #COOKIE} → {@link Dispositivos#porToken} →
 * {@link AutenticacionModelos}: el taller entra con el móvil o la tablet que
 * se dio de alta con un código, sin teclear nada. Sin cookie, con un
 * dispositivo revocado o con su usuario desactivado, la petición sigue sin
 * autenticar (y la cadena contesta 401 donde haga falta entrar).
 *
 * La autenticación vale para esta petición y NO se guarda en la sesión: la
 * credencial es la cookie, que viaja en cada petición, y revocar el
 * dispositivo tiene que cortarlo en la siguiente, no cuando caduque una
 * sesión.
 *
 * No es un bean a propósito: Spring Boot registraría cualquier filtro bean
 * en TODAS las rutas del programa. Lo crea {@link SecurityConfig} solo para la
 * cadena de la app de modelos.
 */
public class FiltroDispositivo extends OncePerRequestFilter {

    /** Nombre de la cookie con el token del dispositivo. */
    public static final String COOKIE = "modelos-dispositivo";

    private final Dispositivos dispositivos;
    private final Usuarios usuarios;
    private final SecurityContextHolderStrategy estrategia;

    public FiltroDispositivo(Dispositivos dispositivos, Usuarios usuarios, SecurityContextHolderStrategy estrategia) {
        this.dispositivos = dispositivos;
        this.usuarios = usuarios;
        this.estrategia = estrategia;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (estrategia.getContext().getAuthentication() == null) {
            String token = tokenDe(request);
            if (token != null) {
                dispositivos.porToken(token).ifPresent(dispositivo -> usuarios.porId(dispositivo.getUsuarioId())
                        .filter(Usuario::isActivo)
                        .ifPresent(usuario -> {
                            SecurityContext contexto = estrategia.createEmptyContext();
                            contexto.setAuthentication(AutenticacionModelos.de(usuario, dispositivo.getId()));
                            estrategia.setContext(contexto);
                            dispositivos.apuntarUso(dispositivo);
                        }));
            }
        }
        chain.doFilter(request, response);
    }

    private static String tokenDe(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst()
                .orElse(null);
    }
}

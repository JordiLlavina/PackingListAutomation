package com.puntotres.packinglist.modelos.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.puntotres.packinglist.modelos.service.Dispositivos;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * Dos cadenas de seguridad: una para la app de modelos y otra para todo lo
 * demás, que se queda exactamente como estaba.
 *
 * <ol>
 * <li><b>La app de modelos</b> ({@code /app/**}, {@code /api/**},
 * {@code /m/**}): la SPA y las páginas de {@code /m} se sirven sin sesión
 * (la SPA enseña ella misma el login) y entrar, mirar si se ha entrado y
 * canjear un código de alta también están abiertos; el resto de la API pide
 * haber entrado, con sesión o con la cookie del dispositivo
 * ({@link FiltroDispositivo}); una sesión de contraseña vuelve a mirar a su
 * usuario en cada petición ({@link RevalidacionSesion}). CSRF por cookie {@code XSRF-TOKEN} que la SPA
 * devuelve en la cabecera {@code X-XSRF-TOKEN}. Los errores son SIEMPRE un
 * JSON con {@code mensaje} y nunca una redirección a un formulario de login,
 * que un {@code fetch} seguiría en silencio y acabaría leyendo HTML.</li>
 * <li><b>El resto del programa</b>: abierto y sin CSRF, como antes de que
 * hubiera Spring Security. Las pantallas viejas hacen {@code fetch} POST sin
 * token y dejarían de funcionar.</li>
 * </ol>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String HACE_FALTA_ENTRAR = "{\"mensaje\":\"Hace falta entrar\"}";
    private static final String SIN_PERMISO = "{\"mensaje\":\"No tienes permiso para esto\"}";

    @Bean
    @Order(1)
    public SecurityFilterChain cadenaModelos(HttpSecurity http, Dispositivos dispositivos, Usuarios usuarios)
            throws Exception {
        PathPatternRequestMatcher.Builder rutas = PathPatternRequestMatcher.withDefaults();
        http.securityMatcher("/app/**", "/api/**", "/m/**")
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/app/**", "/m/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sesion", "/api/sesion/dispositivo").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sesion").permitAll()
                        .anyRequest().authenticated())
                .csrf(c -> c
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Sin enmascarar: la SPA devuelve tal cual el valor de la cookie.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Entrar no puede pedir un token que todavía no se tiene.
                        .ignoringRequestMatchers(rutas.matcher(HttpMethod.POST, "/api/sesion"),
                                rutas.matcher(HttpMethod.POST, "/api/sesion/dispositivo")))
                .addFilterAfter(new CargaTokenCsrf(), CsrfFilter.class)
                .addFilterBefore(new FiltroDispositivo(dispositivos, usuarios,
                        SecurityContextHolder.getContextHolderStrategy()), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new RevalidacionSesion(usuarios,
                        SecurityContextHolder.getContextHolderStrategy()), FiltroDispositivo.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, error) ->
                                json(response, HttpServletResponse.SC_UNAUTHORIZED, HACE_FALTA_ENTRAR))
                        .accessDeniedHandler((request, response, error) ->
                                json(response, HttpServletResponse.SC_FORBIDDEN, SIN_PERMISO)))
                // Un 401 de la API no se guarda para "volver después del login": eso es de formularios.
                .requestCache(r -> r.requestCache(new NullRequestCache()))
                .logout(l -> l.disable());
        return http.build();
    }

    /** La app vieja, como antes de Spring Security: todo abierto y sin CSRF. */
    @Bean
    @Order(2)
    public SecurityFilterChain cadenaResto(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(c -> c.disable());
        return http.build();
    }

    private static void json(HttpServletResponse response, int estado, String cuerpo) throws IOException {
        response.setStatus(estado);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(cuerpo);
    }

    /**
     * Spring Security 6 solo crea el token CSRF (y su cookie) cuando alguien
     * lo lee. La SPA no renderiza nada en el servidor que lo lea, así que sin
     * esto la cookie {@code XSRF-TOKEN} no llegaría nunca y el primer POST
     * daría 403. Leerlo en cada petición de la app de modelos la deja puesta
     * desde la primera página.
     */
    private static final class CargaTokenCsrf implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            Object token = request.getAttribute(CsrfToken.class.getName());
            if (token instanceof CsrfToken csrf) {
                csrf.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}

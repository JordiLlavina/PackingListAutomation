package com.puntotres.packinglist.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Sin identidad no se ve nada.
 *
 * La aplicación se ocupa de su propia seguridad en vez de fiarse de una
 * cabecera que ponga el proxy: así arranca en local sin falsear nada, no se
 * rompe al cambiar de proxy, y si el servidor quedara alcanzable por otra
 * ruta esa cabecera no sería un "soy quien yo diga". Si algún día hay
 * preautenticación en el borde, suma como segunda capa sin que esto se
 * entere.
 */
@Configuration
public class SecurityConfig {

    private final UsuarioOidcService usuarioOidcService;

    public SecurityConfig(UsuarioOidcService usuarioOidcService) {
        this.usuarioOidcService = usuarioOidcService;
    }

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(peticiones -> peticiones
                        // Los estáticos se sirven con el hash del contenido en
                        // el nombre (/estilo-a1b2c3.css), de ahí los comodines.
                        .requestMatchers("/estilo*.css", "/icono-web-*.png", "/favicon.ico").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(login -> login
                        .defaultSuccessUrl("/menu")
                        .userInfoEndpoint(info -> info.oidcUserService(usuarioOidcService)))
                .logout(salida -> salida
                        .logoutUrl("/salir")
                        .logoutSuccessUrl("/menu"))
                .headers(cabeceras -> cabeceras
                        .frameOptions(marco -> marco.deny())
                        .referrerPolicy(politica -> politica.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                // La aplicación no carga nada de ningún CDN: todo
                                // a self. Lo que sí tiene es JavaScript y estilos
                                // EN LÍNEA en cuatro plantillas (entrada, revisión,
                                // resultados, etiquetas de artículo), y por eso
                                // script-src y style-src llevan unsafe-inline:
                                // sin ellos esas pantallas dejan de funcionar y
                                // ningún test lo ve. Las barras del dashboard usan
                                // clases de tramo y no style en línea para no
                                // depender de eso el día que se quite.
                                //
                                // form-action deja salir al login de Microsoft:
                                // el POST de salir acaba redirigido a su dominio,
                                // y sin esto el navegador lo corta sin decir por
                                // qué.
                                "default-src 'self'; script-src 'self' 'unsafe-inline'; "
                                        + "style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; "
                                        + "base-uri 'self'; form-action 'self' https://login.microsoftonline.com")));
        // CSRF queda activo (es el valor por defecto): los formularios
        // Thymeleaf con th:action inyectan el token solos.
        return http.build();
    }
}

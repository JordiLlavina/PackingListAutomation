package com.puntotres.packinglist.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

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
                        .defaultSuccessUrl("/menu"))
                .logout(salida -> salida
                        .logoutUrl("/salir")
                        .logoutSuccessUrl("/menu"));
        // CSRF queda activo (es el valor por defecto): los formularios
        // Thymeleaf con th:action inyectan el token solos.
        return http.build();
    }
}

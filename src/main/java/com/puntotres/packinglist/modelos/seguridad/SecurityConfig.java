package com.puntotres.packinglist.modelos.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadena de seguridad mínima mientras la app de modelos no tiene pantallas.
 *
 * Con Spring Security en el classpath, su autoconfiguración protegería TODAS
 * las rutas del programa (login de formulario y CSRF incluidos) y el
 * asistente de packing list dejaría de funcionar. Esta cadena lo deja todo
 * abierto, como estaba; la Task 6 la acota a la app nueva y deja el resto
 * igual.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaAbierta(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(c -> c.disable());
        return http.build();
    }
}

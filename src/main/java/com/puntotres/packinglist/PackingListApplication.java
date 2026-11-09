package com.puntotres.packinglist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Arranque de Spring Boot. Todavía no expone nada (sin web): sirve para
 * levantar el contexto de beans, cargar application.yml y validar el cableado.
 * La prueba manual del builder sigue en {@link Main}.
 */
// Sin el usuario por defecto de Spring Security (y su contraseña en el log): los usuarios son los de la app de modelos.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
// Las tareas programadas: los recordatorios anti-olvido de la app de modelos, cada hora.
@EnableScheduling
public class PackingListApplication {

    public static void main(String[] args) {
        SpringApplication.run(PackingListApplication.class, args);
    }
}

package com.puntotres.packinglist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Arranque de Spring Boot. Todavía no expone nada (sin web): sirve para
 * levantar el contexto de beans, cargar application.yml y validar el cableado.
 * La prueba manual del builder sigue en {@link Main}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PackingListApplication {

    public static void main(String[] args) {
        SpringApplication.run(PackingListApplication.class, args);
    }
}

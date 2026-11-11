package com.puntotres.packinglist.modelos.service;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La hora con la que se decide si una modificación está vencida
 * ({@link Recordatorios}, el resumen de {@link Modificaciones}). Es un bean
 * para que los tests puedan pararla; en la aplicación es el reloj del sistema.
 */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj() {
        return Clock.systemDefaultZone();
    }
}

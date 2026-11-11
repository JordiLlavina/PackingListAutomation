package com.puntotres.packinglist.modelos.service;

import java.security.GeneralSecurityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.puntotres.packinglist.modelos.ModelosProperties;

/**
 * Elige quién manda los push: {@link EnviadorPushVapid} si hay las dos claves
 * VAPID ({@link ModelosProperties#pushConfigurado()}), y si no
 * {@link EnviadorPushDesactivado}. Unas claves que no se dejan leer apagan el
 * push con un aviso en el log, no la aplicación (y el aviso no dice las
 * claves). Se decide al arrancar: poner las claves pide reiniciar.
 */
@Configuration
public class PushConfig {

    private static final Logger LOG = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    public EnviadorPush enviadorPush(ModelosProperties props) {
        if (!props.pushConfigurado()) {
            return new EnviadorPushDesactivado();
        }
        ModelosProperties.Push push = props.push();
        try {
            return new EnviadorPushVapid(push.vapidPublic(), push.vapidPrivate(), push.asunto());
        } catch (GeneralSecurityException | RuntimeException e) {
            LOG.warn("Las claves VAPID (MODELOS_VAPID_PUBLIC y MODELOS_VAPID_PRIVATE) no se dejan leer: "
                    + "los avisos al móvil quedan apagados ({})", e.getClass().getSimpleName());
            return new EnviadorPushDesactivado();
        }
    }
}

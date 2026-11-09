package com.puntotres.packinglist.modelos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.puntotres.packinglist.modelos.persistence.SuscripcionPush;

/**
 * Sin claves VAPID no se manda nada: la app funciona igual, solo que sin
 * avisos al móvil (la bandeja sigue preguntando cada 20 s).
 */
public class EnviadorPushDesactivado implements EnviadorPush {

    private static final Logger LOG = LoggerFactory.getLogger(EnviadorPushDesactivado.class);

    @Override
    public void enviar(SuscripcionPush s, String payloadJson) {
        LOG.debug("Push sin configurar: no se avisa a la suscripción {}", s.getId());
    }
}

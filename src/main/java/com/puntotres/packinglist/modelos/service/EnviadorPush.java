package com.puntotres.packinglist.modelos.service;

import java.io.IOException;

import com.puntotres.packinglist.modelos.persistence.SuscripcionPush;

/**
 * Manda un aviso a un navegador por Web Push. El de verdad es
 * {@link EnviadorPushVapid}; sin claves VAPID, {@link EnviadorPushDesactivado},
 * que no hace nada. Los tests ponen uno falso. Lo elige {@link PushConfig}.
 */
public interface EnviadorPush {

    /**
     * Manda el aviso, ya en JSON, a esa suscripción. Una suscripción que el
     * navegador ya ha tirado se dice con {@link SuscripcionCaducada} (o con una
     * {@link IOException} que lleve "404" o "410" en el mensaje); cualquier
     * otro fallo, con una {@link IOException} cualquiera.
     */
    void enviar(SuscripcionPush s, String payloadJson) throws IOException;
}

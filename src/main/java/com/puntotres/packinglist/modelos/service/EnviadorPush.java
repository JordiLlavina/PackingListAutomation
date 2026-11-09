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
     * navegador ya ha tirado se dice con {@link SuscripcionCaducada}, que es lo
     * único que la borra; cualquier otro fallo, con una {@link IOException}
     * cualquiera (aunque su mensaje lleve un 404 o un 410, no se borra nada).
     */
    void enviar(SuscripcionPush s, String payloadJson) throws IOException;
}

package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Base64;

import org.apache.http.client.methods.HttpPost;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.modelos.GenerarClavesVapid;
import com.puntotres.packinglist.modelos.persistence.SuscripcionPush;

/**
 * El Web Push de verdad, sin mandar nada: con unas claves VAPID recién hechas
 * y las de un navegador inventado, la librería cifra y firma el aviso en este
 * Java (y con BouncyCastle). Que el navegador lo enseñe solo se puede ver con
 * un navegador.
 */
class EnviadorPushVapidTest {

    @Test
    void cifraYFirmaElAvisoSinMandarlo() throws Exception {
        KeyPair servidor = GenerarClavesVapid.generar();
        KeyPair navegador = GenerarClavesVapid.generar();
        byte[] secreto = new byte[16];
        new SecureRandom().nextBytes(secreto);
        EnviadorPushVapid enviador = new EnviadorPushVapid(GenerarClavesVapid.publica(servidor),
                GenerarClavesVapid.privada(servidor), "mailto:prueba@puntotres.com");
        SuscripcionPush suscripcion = new SuscripcionPush(1L, null, "https://push.example/T17-vapid",
                GenerarClavesVapid.publica(navegador), Base64.getUrlEncoder().withoutPadding().encodeToString(secreto));

        HttpPost peticion = enviador.preparar(suscripcion, "{\"titulo\":\"Orden de despacho: Asa\"}");

        assertEquals("https://push.example/T17-vapid", peticion.getURI().toString());
        assertEquals("aes128gcm", peticion.getFirstHeader("Content-Encoding").getValue());
        assertTrue(peticion.getFirstHeader("Authorization").getValue().startsWith("vapid t="),
                "firmado con VAPID");
        assertTrue(peticion.getEntity().getContentLength() > 0, "el aviso va cifrado en el cuerpo");
    }

    @Test
    void unaSuscripcionConClavesQueNoSeDejanLeerEstaCaducada() throws Exception {
        KeyPair servidor = GenerarClavesVapid.generar();
        EnviadorPushVapid enviador = new EnviadorPushVapid(GenerarClavesVapid.publica(servidor),
                GenerarClavesVapid.privada(servidor), "mailto:prueba@puntotres.com");

        assertThrows(SuscripcionCaducada.class, () -> enviador.enviar(
                new SuscripcionPush(1L, null, "https://push.example/T17-mala", "no-es-una-clave", "x"), "{}"));
    }
}

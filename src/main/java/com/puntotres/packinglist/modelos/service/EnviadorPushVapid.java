package com.puntotres.packinglist.modelos.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;

import com.puntotres.packinglist.modelos.persistence.SuscripcionPush;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

/**
 * El Web Push de verdad, con la librería {@code nl.martijndwars:web-push}:
 * cifra el aviso con las claves de la suscripción (aes128gcm, el del RFC 8291,
 * que es el que piden Safari y los iPad) y lo firma con las claves VAPID del
 * servidor.
 *
 * Un servidor de push que no contesta no puede dejar colgado el hilo de los
 * avisos: se espera como mucho {@link #ESPERA_SEGUNDOS} segundos.
 */
public class EnviadorPushVapid implements EnviadorPush {

    /** Lo más que se espera al servidor de push del navegador. */
    static final int ESPERA_SEGUNDOS = 30;

    private final PushService servicio;

    /**
     * @throws GeneralSecurityException si las claves no se dejan leer
     */
    public EnviadorPushVapid(String clavePublica, String clavePrivada, String asunto)
            throws GeneralSecurityException {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.servicio = new PushService(clavePublica.strip(), clavePrivada.strip(), asunto);
    }

    @Override
    public void enviar(SuscripcionPush s, String payloadJson) throws IOException {
        Notification notificacion = notificacion(s, payloadJson);
        HttpResponse respuesta;
        try {
            Future<HttpResponse> envio = servicio.sendAsync(notificacion, Encoding.AES128GCM);
            try {
                respuesta = envio.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
            } catch (TimeoutException tarde) {
                envio.cancel(true);
                throw new IOException("El servidor de push no ha contestado en " + ESPERA_SEGUNDOS + " s");
            }
        } catch (GeneralSecurityException | JoseException e) {
            throw new IOException("No se ha podido cifrar o firmar el aviso: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            throw new IOException("No se ha podido mandar el aviso: " + e.getCause(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrumpido mandando el aviso", e);
        }
        int estado = respuesta.getStatusLine().getStatusCode();
        String razon = respuesta.getStatusLine().getReasonPhrase();
        if (estado == 404 || estado == 410) {
            throw new SuscripcionCaducada(estado + " " + razon);
        }
        if (estado >= 300) {
            throw new IOException("El servidor de push contesta " + estado + " " + razon);
        }
    }

    /** La petición que se mandaría, cifrada y firmada, sin mandarla (para probarla). */
    HttpPost preparar(SuscripcionPush s, String payloadJson) throws IOException {
        try {
            return servicio.preparePost(notificacion(s, payloadJson), Encoding.AES128GCM);
        } catch (GeneralSecurityException | JoseException e) {
            throw new IOException("No se ha podido cifrar o firmar el aviso: " + e.getMessage(), e);
        }
    }

    private static Notification notificacion(SuscripcionPush s, String payloadJson) throws IOException {
        try {
            return new Notification(s.getEndpoint(), s.getP256dh(), s.getAuth(),
                    payloadJson.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | RuntimeException e) {
            // Unas claves de navegador que no se dejan leer no van a servir nunca.
            throw new SuscripcionCaducada("Las claves de la suscripción no se dejan leer: " + e.getMessage());
        }
    }
}

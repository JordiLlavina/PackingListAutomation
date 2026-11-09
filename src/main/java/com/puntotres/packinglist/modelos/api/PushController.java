package com.puntotres.packinglist.modelos.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.Push;

/**
 * Los avisos al móvil: la clave pública VAPID que pide el navegador para
 * suscribirse, y el alta y la baja de la suscripción de este navegador. Lo
 * puede usar cualquiera que haya entrado, de los dos lados. Qué se manda y a
 * quién es de {@link Push}.
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final Push push;

    public PushController(Push push) {
        this.push = push;
    }

    /** La clave pública y si hay push; sin claves, {@code {"clave":"","configurado":false}}. */
    public record ClavePublica(String clave, boolean configurado) {
    }

    /** Lo que devuelve {@code pushManager.subscribe}: el endpoint y las dos claves del navegador. */
    public record Suscripcion(String endpoint, String p256dh, String auth) {
    }

    public record Baja(String endpoint) {
    }

    @GetMapping("/clave-publica")
    public ClavePublica clavePublica() {
        return new ClavePublica(push.clavePublica(), push.configurado());
    }

    @PostMapping("/suscripcion")
    public ResponseEntity<Void> suscribir(@RequestBody Suscripcion suscripcion,
                                          @AuthenticationPrincipal UsuarioActual quien) {
        push.suscribir(quien, suscripcion.endpoint(), suscripcion.p256dh(), suscripcion.auth());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/suscripcion")
    public ResponseEntity<Void> borrar(@RequestBody Baja baja, @AuthenticationPrincipal UsuarioActual quien) {
        push.borrar(quien, baja.endpoint());
        return ResponseEntity.noContent().build();
    }
}

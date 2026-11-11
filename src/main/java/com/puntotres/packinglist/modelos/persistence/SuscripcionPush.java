package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un navegador (el de la tablet del taller, el del PC de despacho) que ha
 * aceptado recibir avisos: lo que devuelve {@code pushManager.subscribe}. El
 * {@code endpoint} es la dirección del servidor de push del navegador a la que
 * se manda cada aviso, y {@code p256dh} y {@code auth} las claves con las que
 * se cifra para que solo ese navegador lo lea.
 *
 * Un endpoint identifica al navegador, así que está una vez: si en el mismo
 * navegador entra otra persona y se vuelve a suscribir, la suscripción pasa a
 * ser suya. Cuando el servidor de push dice que ya no existe (404 o 410) se
 * borra.
 */
@Entity
@Table(name = "suscripcion_push")
public class SuscripcionPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** El aparato con el que se suscribió; null si entró con contraseña. */
    @Column(name = "dispositivo_id")
    private Long dispositivoId;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    /** Clave pública del navegador (Base64 URL). */
    @Column(name = "p256dh", length = 200, nullable = false)
    private String p256dh;

    /** Secreto de autenticación del navegador (Base64 URL). */
    @Column(name = "auth", length = 100, nullable = false)
    private String auth;

    /** Cuándo se suscribió o renovó la suscripción por última vez. */
    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    protected SuscripcionPush() {
        // Constructor para JPA.
    }

    public SuscripcionPush(Long usuarioId, Long dispositivoId, String endpoint, String p256dh, String auth) {
        this.usuarioId = usuarioId;
        this.dispositivoId = dispositivoId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getDispositivoId() {
        return dispositivoId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    /** El mismo navegador se vuelve a suscribir: puede ser otra persona, otro aparato u otras claves. */
    public void renovar(Long usuarioId, Long dispositivoId, String p256dh, String auth) {
        this.usuarioId = usuarioId;
        this.dispositivoId = dispositivoId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.fecha = LocalDateTime.now();
    }
}

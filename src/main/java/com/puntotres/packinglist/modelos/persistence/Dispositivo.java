package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un móvil o una tablet del taller, dado de alta al entrar con usuario y
 * contraseña: el aparato lleva una cookie con un token que lo identifica y no
 * vuelve a pedir la contraseña. Aquí se guarda solo el hash SHA-256 de ese
 * token; el token en claro se entrega una vez, al entrar.
 *
 * Revocar no lo borra: queda la traza de qué aparato fue y cuándo dejó de
 * valer.
 */
@Entity
@Table(name = "dispositivo")
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Como lo llamó quien lo dio de alta ("Tablet del taller"). */
    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    /** SHA-256 en hexadecimal del token de la cookie. */
    @Column(name = "token_hash", length = 100, nullable = false)
    private String tokenHash;

    @Column(name = "alta", nullable = false)
    private LocalDateTime alta;

    @Column(name = "ultimo_uso")
    private LocalDateTime ultimoUso;

    @Column(name = "revocado_en")
    private LocalDateTime revocadoEn;

    protected Dispositivo() {
        // Constructor para JPA.
    }

    public Dispositivo(Long usuarioId, String nombre, String tokenHash) {
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.tokenHash = tokenHash;
        this.alta = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getAlta() {
        return alta;
    }

    public LocalDateTime getUltimoUso() {
        return ultimoUso;
    }

    public LocalDateTime getRevocadoEn() {
        return revocadoEn;
    }

    public boolean revocado() {
        return revocadoEn != null;
    }

    /** Deja de valer; revocarlo otra vez no cambia la fecha. */
    public void revocar() {
        if (revocadoEn == null) {
            revocadoEn = LocalDateTime.now();
        }
    }

    /** Apunta que se acaba de usar. */
    public void tocar() {
        this.ultimoUso = LocalDateTime.now();
    }
}

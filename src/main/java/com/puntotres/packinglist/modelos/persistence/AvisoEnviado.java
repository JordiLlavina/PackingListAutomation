package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un recordatorio que ya se ha mandado: de qué modificación, a quién, de qué
 * tipo y cuándo. Sirve para no repetirlo dentro de su plazo.
 *
 * Los recordatorios van a un rol entero (todo el taller, todo el despacho),
 * pero {@code usuario_id} es obligatorio en la tabla, así que cada envío deja
 * una fila por destinatario, todas con la misma hora. La regla de no repetir
 * mira la modificación y el tipo, no la persona.
 */
@Entity
@Table(name = "aviso_enviado")
public class AvisoEnviado {

    /** Qué se ha recordado. */
    public enum Tipo {
        /** Una orden sin hacer (al taller) o un aviso sin aplicar del todo (al despacho). */
        RECORDATORIO,
        /** Un aviso del taller al que el despacho no ha puesto prioridad. */
        SIN_PRIORIDAD,
        /** Una pregunta sin contestar. */
        RESPUESTA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "modificacion_id", nullable = false)
    private Long modificacionId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 40, nullable = false)
    private Tipo tipo;

    @Column(name = "enviado_en", nullable = false)
    private LocalDateTime enviadoEn;

    protected AvisoEnviado() {
        // Constructor para JPA.
    }

    public AvisoEnviado(Long modificacionId, Long usuarioId, Tipo tipo, LocalDateTime enviadoEn) {
        this.modificacionId = modificacionId;
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.enviadoEn = enviadoEn;
    }

    public Long getId() {
        return id;
    }

    public Long getModificacionId() {
        return modificacionId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public LocalDateTime getEnviadoEn() {
        return enviadoEn;
    }
}

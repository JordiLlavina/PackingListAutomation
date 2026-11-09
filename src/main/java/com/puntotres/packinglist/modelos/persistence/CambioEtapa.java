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

import com.puntotres.packinglist.persistence.Etapa;

/**
 * Un paso de un modelo de una etapa a otra, con quién lo dio y por qué: la
 * etapa de {@code modelo} solo dice dónde está, y esto es cómo llegó. El
 * primer apunte de un modelo, el del alta, no tiene etapa de origen.
 *
 * El modelo y el usuario son columnas planas, como en el resto del paquete:
 * las claves ajenas viven en el SQL.
 */
@Entity
@Table(name = "cambio_etapa")
public class CambioEtapa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "modelo_id", nullable = false)
    private Long modeloId;

    /** La etapa de la que sale; null en el apunte del alta. */
    @Enumerated(EnumType.STRING)
    @Column(name = "de", length = 20)
    private Etapa de;

    @Enumerated(EnumType.STRING)
    @Column(name = "a", length = 20, nullable = false)
    private Etapa a;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** Quién lo cambió; null si no lo cambió una persona. */
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "motivo", length = 200)
    private String motivo;

    protected CambioEtapa() {
        // Constructor para JPA.
    }

    public CambioEtapa(Long modeloId, Etapa de, Etapa a, LocalDateTime fecha, Long usuarioId, String motivo) {
        this.modeloId = modeloId;
        this.de = de;
        this.a = a;
        this.fecha = fecha;
        this.usuarioId = usuarioId;
        this.motivo = motivo;
    }

    public Long getId() {
        return id;
    }

    public Long getModeloId() {
        return modeloId;
    }

    public Etapa getDe() {
        return de;
    }

    public Etapa getA() {
        return a;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getMotivo() {
        return motivo;
    }
}

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
 * Un sitio donde el despacho aplica un aviso del taller (ICSUITE, el excel de
 * escandallos): si aplica ahí y, si ya está hecho, cuándo y quién. Solo
 * existe en los avisos taller a despacho; lo garantiza el servicio.
 */
@Entity
@Table(name = "modificacion_destino")
public class ModificacionDestino {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "modificacion_id", nullable = false)
    private Long modificacionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "destino", length = 40, nullable = false)
    private DestinoAplicacion destino;

    @Column(name = "aplica", nullable = false)
    private boolean aplica;

    @Column(name = "hecho_en")
    private LocalDateTime hechoEn;

    @Column(name = "hecho_por")
    private Long hechoPor;

    protected ModificacionDestino() {
        // Constructor para JPA.
    }

    /** Nace aplicando y sin hacer. */
    public ModificacionDestino(Long modificacionId, DestinoAplicacion destino) {
        this.modificacionId = modificacionId;
        this.destino = destino;
        this.aplica = true;
    }

    public Long getId() {
        return id;
    }

    public Long getModificacionId() {
        return modificacionId;
    }

    public DestinoAplicacion getDestino() {
        return destino;
    }

    public boolean isAplica() {
        return aplica;
    }

    public LocalDateTime getHechoEn() {
        return hechoEn;
    }

    public Long getHechoPor() {
        return hechoPor;
    }

    public boolean hecho() {
        return hechoEn != null;
    }

    /**
     * Si aplica y si está hecho. Un destino que no aplica no puede estar
     * hecho; uno que ya lo estaba conserva cuándo y quién.
     */
    public void marcar(boolean aplica, boolean hecho, Long usuarioId, LocalDateTime cuando) {
        this.aplica = aplica;
        if (aplica && hecho) {
            if (hechoEn == null) {
                hechoEn = cuando;
                hechoPor = usuarioId;
            }
        } else {
            hechoEn = null;
            hechoPor = null;
        }
    }
}

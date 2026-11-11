package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cómo se ha llamado un modelo y desde cuándo: el código del cliente o, si
 * nació prototipo, su nombre de prototipo. El modelo guarda solo el nombre de
 * hoy; esto es lo que permite seguir encontrándolo por el nombre de ayer.
 *
 * {@code hasta} nulo es el nombre vigente. Una fusión apunta aquí el id del
 * modelo absorbido como {@code "#" + id}, para que su QR siga llevando al
 * superviviente; esos apuntes no son nombres y la ficha no los enseña como tales.
 */
@Entity
@Table(name = "modelo_codigo_historico")
public class ModeloCodigoHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "modelo_id", nullable = false)
    private Long modeloId;

    @Column(name = "codigo", length = 80, nullable = false)
    private String codigo;

    @Column(name = "desde", nullable = false)
    private LocalDateTime desde;

    @Column(name = "hasta")
    private LocalDateTime hasta;

    /** Por qué se apuntó ("ALTA", "FUSION"...). */
    @Column(name = "motivo", length = 200)
    private String motivo;

    protected ModeloCodigoHistorico() {
        // Constructor para JPA.
    }

    public ModeloCodigoHistorico(Long modeloId, String codigo, LocalDateTime desde, String motivo) {
        this.modeloId = modeloId;
        this.codigo = codigo;
        this.desde = desde;
        this.motivo = motivo;
    }

    public Long getId() {
        return id;
    }

    public Long getModeloId() {
        return modeloId;
    }

    public String getCodigo() {
        return codigo;
    }

    public LocalDateTime getDesde() {
        return desde;
    }

    public LocalDateTime getHasta() {
        return hasta;
    }

    public String getMotivo() {
        return motivo;
    }

    /**
     * Pasa a otro modelo: al juntar dos, los nombres del que desaparece (y los
     * {@code "#" + id} de los que ya había absorbido) se van al que se queda,
     * para que una cadena de fusiones siga llevando al último.
     */
    public void moverA(Long modeloId) {
        this.modeloId = modeloId;
    }

    /** Deja de ser el nombre vigente desde {@code cuando}; si ya no lo era, no cambia nada. */
    public void cerrar(LocalDateTime cuando) {
        if (hasta == null) {
            this.hasta = cuando;
        }
    }
}

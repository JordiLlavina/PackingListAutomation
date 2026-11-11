package com.puntotres.packinglist.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Un modelo que ha ido en una temporada. Es la relación N:M entre
 * {@link Modelo} y {@link TemporadaGuardada}: una fila por pareja. Se rellena
 * sola al guardar el excel de una temporada; sirve para filtrar el catálogo,
 * que con tres temporadas tiene cientos de artículos. Cuelga del id del
 * modelo, no de su código: un prototipo puede no tenerlo todavía.
 */
@Entity
@Table(name = "modelo_temporada")
@IdClass(ModeloTemporadaId.class)
public class ModeloTemporada {

    @Id
    @Column(name = "modelo_id")
    private Long modeloId;

    @Id
    @Column(name = "temporada_id")
    private Long temporadaId;

    protected ModeloTemporada() {
        // Constructor para JPA.
    }

    public ModeloTemporada(Long modeloId, Long temporadaId) {
        this.modeloId = modeloId;
        this.temporadaId = temporadaId;
    }

    public Long getModeloId() {
        return modeloId;
    }

    public Long getTemporadaId() {
        return temporadaId;
    }
}

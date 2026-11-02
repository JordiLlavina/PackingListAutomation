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
 * que con tres temporadas tiene cientos de artículos.
 */
@Entity
@Table(name = "modelo_temporada")
@IdClass(ModeloTemporadaId.class)
public class ModeloTemporada {

    @Id
    @Column(name = "cliente", length = 60)
    private String cliente;

    @Id
    @Column(name = "codigo_modelo", length = 80)
    private String codigoModelo;

    @Id
    @Column(name = "temporada_id")
    private Long temporadaId;

    protected ModeloTemporada() {
        // Constructor para JPA.
    }

    public ModeloTemporada(String cliente, String codigoModelo, Long temporadaId) {
        this.cliente = cliente;
        this.codigoModelo = codigoModelo;
        this.temporadaId = temporadaId;
    }

    public String getCliente() {
        return cliente;
    }

    public String getCodigoModelo() {
        return codigoModelo;
    }

    public Long getTemporadaId() {
        return temporadaId;
    }
}

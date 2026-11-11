package com.puntotres.packinglist.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los vínculos modelo ↔ temporada. */
public interface ModeloTemporadaRepository extends JpaRepository<ModeloTemporada, ModeloTemporadaId> {

    List<ModeloTemporada> findByTemporadaId(Long temporadaId);

    List<ModeloTemporada> findByModeloId(Long modeloId);

    void deleteByTemporadaId(Long temporadaId);

    void deleteByModeloId(Long modeloId);
}

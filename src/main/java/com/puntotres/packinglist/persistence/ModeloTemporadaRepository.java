package com.puntotres.packinglist.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los vínculos modelo ↔ temporada. */
public interface ModeloTemporadaRepository extends JpaRepository<ModeloTemporada, ModeloTemporadaId> {

    List<ModeloTemporada> findByClienteAndTemporadaId(String cliente, Long temporadaId);

    List<ModeloTemporada> findByClienteAndCodigoModelo(String cliente, String codigoModelo);

    List<ModeloTemporada> findByCliente(String cliente);

    void deleteByTemporadaId(Long temporadaId);

    void deleteByClienteAndCodigoModelo(String cliente, String codigoModelo);
}

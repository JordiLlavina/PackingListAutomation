package com.puntotres.packinglist.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los artículos del catálogo. La clave es cliente + referencia entera. */
public interface ArticuloRepository extends JpaRepository<Articulo, ArticuloId> {

    List<Articulo> findByClienteOrderByReferencia(String cliente);

    List<Articulo> findByModeloIdOrderByReferencia(Long modeloId);

    /** Los artículos que llevan una piel: para el recorrido de una ficha de la biblioteca. */
    List<Articulo> findByClienteAndRefPielOrderByReferencia(String cliente, String refPiel);

    void deleteByModeloId(Long modeloId);
}

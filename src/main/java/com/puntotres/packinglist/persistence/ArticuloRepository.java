package com.puntotres.packinglist.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los artículos del catálogo. La clave es cliente + referencia entera. */
public interface ArticuloRepository extends JpaRepository<Articulo, ArticuloId> {

    List<Articulo> findByClienteOrderByReferencia(String cliente);

    List<Articulo> findByModeloIdOrderByReferencia(Long modeloId);

    void deleteByModeloId(Long modeloId);
}

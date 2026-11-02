package com.puntotres.packinglist.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los colores de piel de cada artículo en cada color del bolso. */
public interface ArticuloColorRepository extends JpaRepository<ArticuloColor, ArticuloColorId> {

    List<ArticuloColor> findByClienteAndReferenciaOrderByCodigoColor(String cliente, String referencia);

    void deleteByClienteAndReferencia(String cliente, String referencia);

    void deleteByClienteAndReferenciaIn(String cliente, Collection<String> referencias);
}

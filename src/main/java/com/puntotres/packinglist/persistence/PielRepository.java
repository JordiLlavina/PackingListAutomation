package com.puntotres.packinglist.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las pieles de cada cliente. La clave es cliente + referencia de piel. */
public interface PielRepository extends JpaRepository<Piel, PielId> {

    List<Piel> findByClienteOrderByRefPiel(String cliente);

    /** Las pieles de cliente enlazadas a una ficha de la biblioteca. */
    List<Piel> findByFichaPielIdOrderByClienteAscRefPielAsc(Long fichaPielId);

    List<Piel> findByFichaPielIdIsNotNull();

    /** Las que aún no se sabe qué piel de qué proveedor son. */
    List<Piel> findByFichaPielIdIsNullOrderByClienteAscRefPielAsc();
}

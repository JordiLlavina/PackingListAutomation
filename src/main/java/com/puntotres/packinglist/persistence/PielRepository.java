package com.puntotres.packinglist.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las pieles de cada cliente. La clave es cliente + referencia de piel. */
public interface PielRepository extends JpaRepository<Piel, PielId> {

    List<Piel> findByClienteOrderByRefPiel(String cliente);
}

package com.puntotres.packinglist.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso al forro y las combinaciones recordados. La clave es cliente + referencia. */
public interface MemoriaPielesArticuloRepository
        extends JpaRepository<MemoriaPielesArticulo, MemoriaPielesArticuloId> {
}

package com.puntotres.packinglist.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la memoria de referencias. La clave es cliente + referencia. */
public interface MemoriaCajaReferenciaRepository
        extends JpaRepository<MemoriaCajaReferencia, MemoriaCajaReferenciaId> {
}

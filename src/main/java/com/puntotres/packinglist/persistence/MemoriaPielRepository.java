package com.puntotres.packinglist.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los nombres de piel recordados. La clave es cliente + referencia de piel. */
public interface MemoriaPielRepository extends JpaRepository<MemoriaPiel, MemoriaPielId> {
}

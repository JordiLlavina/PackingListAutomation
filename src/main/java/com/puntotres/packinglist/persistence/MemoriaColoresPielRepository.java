package com.puntotres.packinglist.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los colores de piel recordados. La clave es cliente + referencia + código de color. */
public interface MemoriaColoresPielRepository
        extends JpaRepository<MemoriaColoresPiel, MemoriaColoresPielId> {
}

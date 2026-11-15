package com.puntotres.packinglist.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los eventos apuntados del recorrido de una piel. */
public interface EventoPielRepository extends JpaRepository<EventoPiel, Long> {

    List<EventoPiel> findByFichaPielIdOrderByFechaAscIdAsc(Long fichaPielId);
}

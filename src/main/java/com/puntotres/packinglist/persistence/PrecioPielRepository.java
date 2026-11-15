package com.puntotres.packinglist.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los tramos de precio de las fichas de piel. */
public interface PrecioPielRepository extends JpaRepository<PrecioPiel, Long> {

    List<PrecioPiel> findByFichaPielId(Long fichaPielId);

    List<PrecioPiel> findByFichaPielIdIn(Collection<Long> fichas);

    void deleteByFichaPielId(Long fichaPielId);
}

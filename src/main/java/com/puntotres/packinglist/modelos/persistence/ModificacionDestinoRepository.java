package com.puntotres.packinglist.modelos.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Los destinos de los avisos del taller, en el orden en que se crearon (el del yml). */
public interface ModificacionDestinoRepository extends JpaRepository<ModificacionDestino, Long> {

    List<ModificacionDestino> findByModificacionIdOrderByIdAsc(Long modificacionId);

    List<ModificacionDestino> findByModificacionIdInOrderByIdAsc(Collection<Long> modificacionIds);
}

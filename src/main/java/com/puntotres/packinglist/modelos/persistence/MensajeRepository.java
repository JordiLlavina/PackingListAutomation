package com.puntotres.packinglist.modelos.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Los mensajes de las modificaciones, del más viejo al más nuevo. A la misma
 * hora desempata el id: dos mensajes seguidos pueden caer en el mismo
 * instante y el orden de una conversación no puede bailar.
 */
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    List<Mensaje> findByModificacionIdOrderByCreadoEnAscIdAsc(Long modificacionId);

    List<Mensaje> findByModificacionIdInOrderByCreadoEnAscIdAsc(Collection<Long> modificacionIds);

    Optional<Mensaje> findFirstByModificacionIdOrderByCreadoEnDescIdDesc(Long modificacionId);
}

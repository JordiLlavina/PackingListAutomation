package com.puntotres.packinglist.modelos.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Las suscripciones al Web Push; el endpoint identifica al navegador. */
public interface SuscripcionPushRepository extends JpaRepository<SuscripcionPush, Long> {

    Optional<SuscripcionPush> findFirstByEndpoint(String endpoint);

    List<SuscripcionPush> findByUsuarioIdIn(Collection<Long> usuarioIds);
}

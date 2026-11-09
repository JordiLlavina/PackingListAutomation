package com.puntotres.packinglist.modelos.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Las modificaciones. Los filtros de la lista que no son la dirección y el
 * estado (prioridad, modelo, cliente, texto) los aplica el servicio en Java
 * sobre estos candidatos: son cientos, no millones, y el texto busca además en
 * mensajes y transcripciones, que viven en otras tablas.
 */
public interface ModificacionRepository extends JpaRepository<Modificacion, Long> {

    List<Modificacion> findByDireccion(Direccion direccion);

    List<Modificacion> findByDireccionAndEstadoIn(Direccion direccion, Collection<EstadoModificacion> estados);

    List<Modificacion> findByEstadoIn(Collection<EstadoModificacion> estados);

    /** Las de un modelo, de la más nueva a la más vieja (a la misma hora, la última creada primero). */
    List<Modificacion> findByModeloIdOrderByCreadaEnDescIdDesc(Long modeloId);

    long countByDireccionAndEstadoIn(Direccion direccion, Collection<EstadoModificacion> estados);

    long countByDireccionAndPrioridadIsNullAndEstadoIn(Direccion direccion, Collection<EstadoModificacion> estados);

    List<Modificacion> findByDireccionAndNecesitaRespuestaTrue(Direccion direccion);

    List<Modificacion> findByDireccionAndNecesitaRespuestaTrueAndCreadaPor(Direccion direccion, Long creadaPor);
}

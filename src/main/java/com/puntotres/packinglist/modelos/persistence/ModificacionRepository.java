package com.puntotres.packinglist.modelos.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    long countByModeloId(Long modeloId);

    /** Si el modelo tiene alguna modificación: es historial del taller y el catálogo no puede borrarlo. */
    boolean existsByModeloId(Long modeloId);

    long countByDireccionAndEstadoIn(Direccion direccion, Collection<EstadoModificacion> estados);

    long countByDireccionAndPrioridadIsNullAndEstadoIn(Direccion direccion, Collection<EstadoModificacion> estados);

    List<Modificacion> findByDireccionAndNecesitaRespuestaTrue(Direccion direccion);

    List<Modificacion> findByDireccionAndNecesitaRespuestaTrueAndCreadaPor(Direccion direccion, Long creadaPor);

    /**
     * Pone el título de la IA a un aviso del taller solo si todavía no tiene
     * ninguno, en una sola sentencia: si mientras se resumía la nota de voz el
     * despacho ha puesto o corregido el título, no se pisa. Devuelve cuántas
     * filas ha cambiado (0 o 1).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Modificacion m set m.titulo = :titulo, m.tituloDeIa = true
            where m.id = :id and m.direccion = :direccion and (m.titulo is null or trim(m.titulo) = '')""")
    int ponerTituloDeIaSiNoTiene(@Param("id") Long id, @Param("direccion") Direccion direccion,
                                 @Param("titulo") String titulo);
}

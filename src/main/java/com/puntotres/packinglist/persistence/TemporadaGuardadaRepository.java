package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a las temporadas guardadas.
 *
 * Las consultas de listado devuelven {@link ResumenTemporada} y no la entidad
 * para no arrastrar el excel de cada temporada cada vez que se pinta un
 * desplegable.
 */
public interface TemporadaGuardadaRepository extends JpaRepository<TemporadaGuardada, Long> {

    String RESUMEN = """
            select new com.puntotres.packinglist.persistence.ResumenTemporada(
                t.id, t.cliente, t.temporada, t.nombreFichero, t.fechaActualizacion)
            from TemporadaGuardada t""";

    @Query(RESUMEN + " order by t.cliente, t.temporada")
    List<ResumenTemporada> resumenes();

    @Query(RESUMEN + " where t.cliente = :cliente order by t.temporada")
    List<ResumenTemporada> resumenesDe(@Param("cliente") String cliente);

    Optional<TemporadaGuardada> findByClienteAndTemporadaIgnoreCase(String cliente, String temporada);
}

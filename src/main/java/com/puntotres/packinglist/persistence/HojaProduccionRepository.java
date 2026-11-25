package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a las Hojas de Producción guardadas. */
public interface HojaProduccionRepository extends JpaRepository<HojaProduccion, Long> {

    /**
     * El listado de antiguas, la más reciente primero, por una proyección: ni
     * el snapshot ni la foto (cientos de KB cada una) hacen falta para listar.
     */
    @Query("select new com.puntotres.packinglist.persistence.ResumenHojaProduccion(h.id, h.fecha, h.cliente, "
            + "h.temporada, h.modelo, h.nombreBolso, h.taller, h.numero, h.fechaSalida) "
            + "from HojaProduccion h order by h.fecha desc, h.id desc")
    List<ResumenHojaProduccion> resumenes();

    /** La foto subida con la hoja, o vacío si no tiene (usa la del catálogo). */
    @Query("select h.foto from HojaProduccion h where h.id = :id and h.foto is not null")
    Optional<byte[]> fotoDe(@Param("id") Long id);

    /**
     * El mayor número de hoja de un taller, o null si no tiene ninguna.
     * {@code clave} es el taller ya recortado y en mayúsculas: «Sweet Bag» y
     * «SWEET BAG » son el mismo taller.
     */
    @Query("select max(h.numero) from HojaProduccion h where upper(trim(h.taller)) = :clave")
    Integer maximoNumeroDe(@Param("clave") String clave);

    /** Pares (taller recortado y en mayúsculas, mayor número de hoja), uno por taller. */
    @Query("select upper(trim(h.taller)), max(h.numero) from HojaProduccion h "
            + "where h.taller is not null and trim(h.taller) <> '' group by upper(trim(h.taller))")
    List<Object[]> maximosPorTaller();

    /** Los talleres usados antes, distintos y sin vacíos. */
    @Query("select distinct h.taller from HojaProduccion h where h.taller is not null and h.taller <> '' order by h.taller")
    List<String> talleres();
}

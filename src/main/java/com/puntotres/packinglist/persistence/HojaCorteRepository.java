package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a las Hojas de corte guardadas. */
public interface HojaCorteRepository extends JpaRepository<HojaCorte, Long> {

    /** El listado de antiguas, la más reciente primero, sin el snapshot ni la foto. */
    @Query("select new com.puntotres.packinglist.persistence.ResumenHojaCorte(h.id, h.fecha, h.cliente, "
            + "h.temporada, h.modelo, h.nombreBolso, h.colores, h.foto is not null) "
            + "from HojaCorte h order by h.fecha desc, h.id desc")
    List<ResumenHojaCorte> resumenes();

    @Query("select h.foto from HojaCorte h where h.id = :id and h.foto is not null")
    Optional<byte[]> fotoDe(@Param("id") Long id);
}

package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a las hojas de «Info de embalaje» guardadas. */
public interface HojaEmbalajeRepository extends JpaRepository<HojaEmbalaje, Long> {

    List<HojaEmbalaje> findAllByOrderByFechaDescIdDesc();

    /** La hoja más reciente del mismo cliente y modelo: de ella se prerrellenan bolsa, etiqueta e instrucción. */
    Optional<HojaEmbalaje> findFirstByClienteAndCodigoModeloOrderByFechaDescIdDesc(String cliente, String codigoModelo);

    /**
     * El mayor número de envío de un taller, o null si no tiene ninguna hoja.
     * {@code clave} es el taller ya recortado y en mayúsculas: se compara sin
     * espacios alrededor ni mayúsculas, así que «Tánger» y «TÁNGER » son el mismo.
     */
    @Query("select max(h.numeroEnvio) from HojaEmbalaje h where upper(trim(h.taller)) = :clave")
    Integer maximoNumeroEnvioDe(@Param("clave") String clave);

    /** Pares (taller recortado y en mayúsculas, mayor número de envío), uno por taller. */
    @Query("select upper(trim(h.taller)), max(h.numeroEnvio) from HojaEmbalaje h "
            + "where h.taller is not null and trim(h.taller) <> '' group by upper(trim(h.taller))")
    List<Object[]> maximosPorTaller();

    /** Los talleres usados antes, distintos y sin vacíos, para el desplegable de la hoja. */
    @Query("select distinct h.taller from HojaEmbalaje h where h.taller is not null and h.taller <> '' order by h.taller")
    List<String> talleres();
}

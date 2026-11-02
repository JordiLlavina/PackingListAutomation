package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a las hojas de «Info de embalaje» guardadas. */
public interface HojaEmbalajeRepository extends JpaRepository<HojaEmbalaje, Long> {

    List<HojaEmbalaje> findAllByOrderByFechaDesc();

    /** La hoja más reciente del mismo cliente y modelo: de ella se prerrellenan bolsa, etiqueta e instrucción. */
    Optional<HojaEmbalaje> findFirstByClienteAndCodigoModeloOrderByFechaDesc(String cliente, String codigoModelo);

    /** El mayor número de envío guardado, o null si no hay ninguna hoja. */
    @Query("select max(h.numeroEnvio) from HojaEmbalaje h")
    Integer maximoNumeroEnvio();

    /** Los talleres usados antes, distintos y sin vacíos, para el desplegable de la hoja. */
    @Query("select distinct h.taller from HojaEmbalaje h where h.taller is not null and h.taller <> '' order by h.taller")
    List<String> talleres();
}

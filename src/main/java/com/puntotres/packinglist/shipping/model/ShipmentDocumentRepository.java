package com.puntotres.packinglist.shipping.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentDocumentRepository extends JpaRepository<ShipmentDocument, Long> {

    Optional<ShipmentDocument> findFirstByEnvioIdAndTipo(Long envioId, String tipo);

    /** Solo los tipos, sin cargar los PDF: es lo que necesita la pantalla para pintar los botones. */
    @Query("select d.tipo from ShipmentDocument d where d.envio.id = :envioId order by d.id")
    List<String> tiposDe(@Param("envioId") Long envioId);
}

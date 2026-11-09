package com.puntotres.packinglist.modelos.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Los cambios de etapa de los modelos, del más nuevo al más viejo. */
public interface CambioEtapaRepository extends JpaRepository<CambioEtapa, Long> {

    List<CambioEtapa> findByModeloIdOrderByFechaDesc(Long modeloId);
}

package com.puntotres.packinglist.modelos.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Los cambios de etapa de los modelos, del más nuevo al más viejo. A la misma
 * hora desempata el id (el último apuntado, primero): dos cambios seguidos
 * pueden caer en el mismo instante y sin desempate la base de datos los
 * devuelve en cualquier orden.
 */
public interface CambioEtapaRepository extends JpaRepository<CambioEtapa, Long> {

    List<CambioEtapa> findByModeloIdOrderByFechaDescIdDesc(Long modeloId);
}

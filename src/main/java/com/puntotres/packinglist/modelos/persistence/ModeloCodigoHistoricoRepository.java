package com.puntotres.packinglist.modelos.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Los nombres que ha tenido cada modelo, del más nuevo al más viejo; a la misma hora, el último apuntado primero. */
public interface ModeloCodigoHistoricoRepository extends JpaRepository<ModeloCodigoHistorico, Long> {

    List<ModeloCodigoHistorico> findByModeloIdOrderByDesdeDescIdDesc(Long modeloId);

    /** El apunte con ese código exacto; para encontrar un modelo por un id que ya no existe ({@code "#" + id}). */
    Optional<ModeloCodigoHistorico> findFirstByCodigo(String codigo);
}

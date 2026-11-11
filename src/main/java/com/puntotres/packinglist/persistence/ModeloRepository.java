package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a los modelos del catálogo. La clave es un id interno; un modelo con
 * código es único por cliente + código, y un prototipo sin código, por
 * cliente + nombre de prototipo + temporada en que nació.
 */
public interface ModeloRepository extends JpaRepository<Modelo, Long> {

    Optional<Modelo> findByClienteAndCodigo(String cliente, String codigo);

    List<Modelo> findByClienteOrderByCodigoAscNombrePrototipoAsc(String cliente);

    List<Modelo> findByClienteAndEtapaOrderByCodigoAscNombrePrototipoAsc(String cliente, Etapa etapa);

    Optional<Modelo> findByClienteAndNombrePrototipoAndTemporadaAltaId(String cliente, String nombrePrototipo,
                                                                       Long temporadaAltaId);
}

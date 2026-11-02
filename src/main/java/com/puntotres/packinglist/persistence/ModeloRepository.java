package com.puntotres.packinglist.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los modelos del catálogo. La clave es cliente + código del modelo. */
public interface ModeloRepository extends JpaRepository<Modelo, ModeloId> {

    List<Modelo> findByClienteOrderByCodigo(String cliente);
}

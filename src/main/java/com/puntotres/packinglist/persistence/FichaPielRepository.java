package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las fichas de piel. Una ficha es única por proveedor + nombre. */
public interface FichaPielRepository extends JpaRepository<FichaPiel, Long> {

    Optional<FichaPiel> findByProveedorIdAndNombre(Long proveedorId, String nombre);

    List<FichaPiel> findByProveedorIdOrderByNombre(Long proveedorId);

    List<FichaPiel> findByNombre(String nombre);

    long countByProveedorId(Long proveedorId);
}

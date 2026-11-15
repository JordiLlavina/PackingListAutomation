package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los proveedores de pieles. El nombre es único. */
public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    Optional<Proveedor> findByNombre(String nombre);

    List<Proveedor> findAllByOrderByNombre();
}

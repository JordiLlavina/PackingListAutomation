package com.puntotres.packinglist.shipping.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentReasonRepository extends JpaRepository<ShipmentReason, Long> {
    List<ShipmentReason> findByActivoTrueOrderByNombreAsc();
    List<ShipmentReason> findAllByOrderByNombreAsc();
    Optional<ShipmentReason> findByNombreIgnoreCase(String nombre);
}

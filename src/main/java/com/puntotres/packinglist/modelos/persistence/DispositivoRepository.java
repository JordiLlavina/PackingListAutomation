package com.puntotres.packinglist.modelos.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los dispositivos dados de alta, que se buscan por el hash de su token. */
public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    Optional<Dispositivo> findByTokenHash(String tokenHash);
}

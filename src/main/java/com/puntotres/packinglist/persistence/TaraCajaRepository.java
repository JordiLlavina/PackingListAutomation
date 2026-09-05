package com.puntotres.packinglist.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la tabla de taras. La clave es la medida ya normalizada. */
public interface TaraCajaRepository extends JpaRepository<TaraCaja, String> {
}

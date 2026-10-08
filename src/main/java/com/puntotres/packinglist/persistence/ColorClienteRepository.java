package com.puntotres.packinglist.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la Tabla colores Clientes. La clave es cliente + código. */
public interface ColorClienteRepository extends JpaRepository<ColorCliente, ColorClienteId> {

    List<ColorCliente> findByClienteOrderByCodigo(String cliente);
}

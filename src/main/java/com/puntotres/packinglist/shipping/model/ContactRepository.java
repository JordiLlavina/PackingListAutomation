package com.puntotres.packinglist.shipping.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByActivoTrueOrderByEmpresaAscPersonaAsc();
    List<Contact> findByEmpresaIgnoreCaseAndPersonaIgnoreCase(String empresa, String persona);
}

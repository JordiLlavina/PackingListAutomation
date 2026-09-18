package com.puntotres.packinglist.shipping.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingAccountRepository extends JpaRepository<BillingAccount, Long> {
    Optional<BillingAccount> findByNumero(String numero);
    List<BillingAccount> findByActivaTrueOrderByPropiaDescTitularAsc();
    List<BillingAccount> findAllByOrderByPropiaDescTitularAsc();
}

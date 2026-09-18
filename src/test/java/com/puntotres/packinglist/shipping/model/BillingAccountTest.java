package com.puntotres.packinglist.shipping.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

class BillingAccountTest {

    @Test
    void seCreaSinUsosConUnSetVacioQueNoEsEnumSet() {
        BillingAccount cuenta = new BillingAccount("300112049", "Puntotres", true, Set.of());

        assertTrue(cuenta.getRoles().isEmpty());
        assertFalse(cuenta.permite(BillingRole.PAYER));
    }

    @Test
    void anadirRolDespuesDeCrearlaSinUsosLaHabilitaParaEseRol() {
        BillingAccount cuenta = new BillingAccount("300112049", "Puntotres", true, Set.of());

        cuenta.anadirRol(BillingRole.PAYER);

        assertTrue(cuenta.permite(BillingRole.PAYER));
    }
}

package com.puntotres.packinglist.shipping.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.shipping.model.BillingAccount;
import com.puntotres.packinglist.shipping.model.BillingAccountRepository;
import com.puntotres.packinglist.shipping.model.BillingRole;

class BillingAccountServiceTest {

    private final BillingAccountRepository repo = mock(BillingAccountRepository.class);
    private final BillingAccountService servicio = new BillingAccountService(repo);

    @Test
    void elNumeroSeNormalizaYNoPuedeRepetirse() {
        when(repo.findByNumero("956483537")).thenReturn(Optional.of(new BillingAccount("956483537", "AMI", false, EnumSet.of(BillingRole.PAYER))));

        DatoInvalidoException e = assertThrows(DatoInvalidoException.class,
                () -> servicio.guardar(null, " 956 483 537 ", "AMI PARIS", false, EnumSet.of(BillingRole.PAYER)));
        assertTrue(e.getMessage().contains("956483537"));
    }

    @Test
    void unaCuentaSinRolesNoSirveParaNada() {
        assertThrows(DatoInvalidoException.class, () -> servicio.guardar(null, "123456789", "X", false, EnumSet.noneOf(BillingRole.class)));
    }

    @Test
    void asegurarDaDeAltaSiNoExisteYSiExisteSoloAnadeRoles() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByNumero("300112049")).thenReturn(Optional.empty());

        BillingAccount nueva = servicio.asegurar("300112049", "Puntotres", true, EnumSet.of(BillingRole.SHIPPER));
        assertTrue(nueva.isPropia());
        assertEquals(EnumSet.of(BillingRole.SHIPPER), nueva.getRoles());

        when(repo.findByNumero("300112049")).thenReturn(Optional.of(nueva));
        BillingAccount misma = servicio.asegurar("300112049", "Otro titular", false, EnumSet.of(BillingRole.PAYER));
        assertSame(nueva, misma);
        assertEquals("Puntotres", misma.getTitular(), "el titular no se pisa");
        assertTrue(misma.isPropia(), "propia no se pisa");
        assertEquals(EnumSet.of(BillingRole.SHIPPER, BillingRole.PAYER), misma.getRoles());
    }
}

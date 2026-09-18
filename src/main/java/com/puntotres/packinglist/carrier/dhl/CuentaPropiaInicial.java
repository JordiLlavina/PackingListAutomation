package com.puntotres.packinglist.carrier.dhl;

import java.util.EnumSet;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.puntotres.packinglist.shipping.model.BillingRole;
import com.puntotres.packinglist.shipping.service.BillingAccountService;

/**
 * Da de alta la cuenta de DHL_SHIPPER_ACCOUNT como cuenta propia al arrancar,
 * si no está: DHL exige siempre accounts[shipper] con ella, y sin fila en la
 * tabla no aparecería en el desplegable de "cuenta de transporte".
 */
@Component
public class CuentaPropiaInicial {

    private final DhlProperties props;
    private final BillingAccountService cuentas;

    public CuentaPropiaInicial(DhlProperties props, BillingAccountService cuentas) {
        this.props = props;
        this.cuentas = cuentas;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void asegurar() {
        if (!props.getShipperAccount().isBlank()) {
            cuentas.asegurar(props.getShipperAccount(), "Puntotres (cuenta propia DHL)", true, EnumSet.allOf(BillingRole.class));
        }
    }
}

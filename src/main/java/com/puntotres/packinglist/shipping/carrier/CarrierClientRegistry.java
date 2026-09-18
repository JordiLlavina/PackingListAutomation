package com.puntotres.packinglist.shipping.carrier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.shipping.model.CarrierCode;

/** Adaptador de cada transportista, por código. Spring inyecta todos los CarrierClient que haya. */
@Component
public class CarrierClientRegistry {

    private final Map<CarrierCode, CarrierClient> clientes = new EnumMap<>(CarrierCode.class);

    public CarrierClientRegistry(List<CarrierClient> disponibles) {
        for (CarrierClient cliente : disponibles) {
            clientes.put(cliente.codigo(), cliente);
        }
    }

    public CarrierClient para(CarrierCode codigo) {
        CarrierClient cliente = clientes.get(codigo);
        if (cliente == null) {
            throw new CarrierException("No hay adaptador para el transportista " + codigo);
        }
        return cliente;
    }
}

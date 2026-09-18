package com.puntotres.packinglist.shipping.service;

import java.util.List;

import com.puntotres.packinglist.shipping.model.Shipment;

/** Resultado de tarifar: o hay ofertas, o hay problemas de datos, o el transportista ha fallado. */
public record ResultadoTarifa(Shipment envio, List<String> problemas, String errorTransportista) {
    public boolean ok() { return problemas.isEmpty() && errorTransportista == null; }
}

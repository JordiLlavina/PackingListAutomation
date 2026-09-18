package com.puntotres.packinglist.shipping.service;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.shipping.model.Shipment;

@Component
public class NoOpNotifier implements Notifier {
    @Override public void envioCreado(Shipment envio) { }
    @Override public void envioFallido(Shipment envio, String error) { }
}

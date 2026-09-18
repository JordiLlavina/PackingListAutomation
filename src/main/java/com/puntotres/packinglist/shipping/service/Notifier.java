package com.puntotres.packinglist.shipping.service;

import com.puntotres.packinglist.shipping.model.Shipment;

/**
 * Avisos al exterior cuando un envío cambia. En la fase 1 no hay ninguno;
 * las fases siguientes añaden correo (Microsoft 365) y Telegram.
 */
public interface Notifier {
    void envioCreado(Shipment envio);
    void envioFallido(Shipment envio, String error);
}

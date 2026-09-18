package com.puntotres.packinglist.shipping.service;

import java.time.LocalDate;

import com.puntotres.packinglist.shipping.model.Direction;
import com.puntotres.packinglist.shipping.model.ShipmentStatus;

/** Filtros del listado. Todo opcional; null = no filtrar por eso. */
public record FiltroEnvios(LocalDate desde, LocalDate hasta, ShipmentStatus estado, Long motivoId, String cliente, Direction sentido) {
    public static FiltroEnvios vacio() { return new FiltroEnvios(null, null, null, null, null, null); }
}

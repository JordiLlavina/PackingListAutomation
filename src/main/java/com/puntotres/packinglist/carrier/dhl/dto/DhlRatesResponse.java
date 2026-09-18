package com.puntotres.packinglist.carrier.dhl.dto;

import java.util.List;

/** Respuesta de POST /rates. */
public record DhlRatesResponse(List<DhlProduct> products) { }

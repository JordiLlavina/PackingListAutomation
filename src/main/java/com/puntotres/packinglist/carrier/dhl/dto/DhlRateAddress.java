package com.puntotres.packinglist.carrier.dhl.dto;

/** Direccion tal como la pide POST /rates: sin contacto ni registros fiscales. */
public record DhlRateAddress(String postalCode, String cityName, String countryCode,
                             String addressLine1, String addressLine2, String addressLine3) { }

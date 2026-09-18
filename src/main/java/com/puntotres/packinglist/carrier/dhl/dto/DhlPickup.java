package com.puntotres.packinglist.carrier.dhl.dto;

/** closeTime en formato HH:MM; sin pickupDetails, DHL recoge en la direccion del remitente. */
public record DhlPickup(Boolean isRequested, String closeTime, String location) { }

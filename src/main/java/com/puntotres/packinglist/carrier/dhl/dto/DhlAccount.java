package com.puntotres.packinglist.carrier.dhl.dto;

/** Una cuenta DHL en la petición: typeCode shipper | payer | duties-taxes. */
public record DhlAccount(String typeCode, String number) { }

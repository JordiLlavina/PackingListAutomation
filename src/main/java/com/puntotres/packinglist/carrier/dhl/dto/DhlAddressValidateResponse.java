package com.puntotres.packinglist.carrier.dhl.dto;

import java.util.List;

/** Respuesta de GET /address-validate. */
public record DhlAddressValidateResponse(List<String> warnings, List<DhlValidatedAddress> address) { }

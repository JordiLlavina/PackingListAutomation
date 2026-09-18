package com.puntotres.packinglist.carrier.dhl.dto;

import java.util.List;

/** Cuerpo de error de MyDHL API (application/problem+json). */
public record DhlErrorResponse(String instance, String detail, String title, String message, List<String> additionalDetails, String status) { }

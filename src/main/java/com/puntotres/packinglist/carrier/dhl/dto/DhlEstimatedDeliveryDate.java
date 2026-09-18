package com.puntotres.packinglist.carrier.dhl.dto;

/** QDDC: pide a DHL que calcule la fecha estimada de entrega. */
public record DhlEstimatedDeliveryDate(Boolean isRequested, String typeCode) { }

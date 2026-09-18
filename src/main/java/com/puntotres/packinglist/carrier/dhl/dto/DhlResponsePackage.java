package com.puntotres.packinglist.carrier.dhl.dto;

public record DhlResponsePackage(Integer referenceNumber, String trackingNumber, String trackingUrl) { }

package com.puntotres.packinglist.carrier.dhl.dto;

public record DhlPostalAddress(String postalCode, String cityName, String countryCode, String provinceCode,
                               String addressLine1, String addressLine2, String addressLine3) { }

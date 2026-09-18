package com.puntotres.packinglist.carrier.dhl.dto;

public record DhlValidatedAddress(String countryCode, String postalCode, String cityName, String countyName, DhlServiceArea serviceArea) { }

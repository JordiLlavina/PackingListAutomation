package com.puntotres.packinglist.carrier.dhl.dto;

import java.math.BigDecimal;

/** typeCode: declaredValue. */
public record DhlMonetaryAmount(String typeCode, BigDecimal value, String currency) { }

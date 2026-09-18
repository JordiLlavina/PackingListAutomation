package com.puntotres.packinglist.carrier.dhl.dto;

import java.math.BigDecimal;

/** currencyType: BILLC (facturado) | PULCL | BASEC. */
public record DhlTotalPrice(String currencyType, String priceCurrency, BigDecimal price) { }

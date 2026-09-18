package com.puntotres.packinglist.carrier.dhl.dto;

import java.math.BigDecimal;
import java.util.List;

public record DhlLineItem(Integer number, String description, BigDecimal price, DhlQuantity quantity,
                          List<DhlCommodityCode> commodityCodes, String exportReasonType, String manufacturerCountry,
                          DhlLineWeight weight) { }

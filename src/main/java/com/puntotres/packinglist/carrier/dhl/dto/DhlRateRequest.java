package com.puntotres.packinglist.carrier.dhl.dto;

import java.util.List;

/** Cuerpo de POST /rates. */
public record DhlRateRequest(DhlRateCustomerDetails customerDetails, List<DhlAccount> accounts,
                             String plannedShippingDateAndTime, String unitOfMeasurement, Boolean isCustomsDeclarable,
                             List<DhlMonetaryAmount> monetaryAmount, DhlEstimatedDeliveryDate estimatedDeliveryDate,
                             String productTypeCode, List<DhlRatePackage> packages) { }

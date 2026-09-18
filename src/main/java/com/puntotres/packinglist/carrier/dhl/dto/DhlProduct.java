package com.puntotres.packinglist.carrier.dhl.dto;

import java.util.List;

public record DhlProduct(String productName, String productCode, String localProductCode,
                         List<DhlTotalPrice> totalPrice, DhlDeliveryCapabilities deliveryCapabilities) { }

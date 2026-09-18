package com.puntotres.packinglist.shipping.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.puntotres.packinglist.shipping.model.RateOffer;

public record OfferDto(String productCode, String productName, BigDecimal price, String currency, LocalDateTime estimatedDelivery) {
    static OfferDto de(RateOffer o) { return new OfferDto(o.getProductoCodigo(), o.getProductoNombre(), o.getPrecio(), o.getMoneda(), o.getEntregaEstimada()); }
}

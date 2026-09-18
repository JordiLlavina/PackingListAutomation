package com.puntotres.packinglist.shipping.carrier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductOffer(String codigo, String nombre, BigDecimal precio, String moneda, LocalDateTime entregaEstimada) { }

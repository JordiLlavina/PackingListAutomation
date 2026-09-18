package com.puntotres.packinglist.shipping.carrier;

import java.time.LocalDateTime;

public record TrackingEvent(LocalDateTime fecha, String codigo, String descripcion, String lugar) { }

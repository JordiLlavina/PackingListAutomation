package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

/** Una sesión de packing guardada vista desde la lista: sin el envío ni el excel de pedido. */
public record ResumenSesionPacking(Long id, String cliente, LocalDateTime creada, String temporada,
                                   String facturas, String destinos) {
}

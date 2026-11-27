package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

/** Una Hoja de corte guardada vista desde el listado de antiguas: sin el snapshot ni la foto. */
public record ResumenHojaCorte(Long id, LocalDateTime fecha, String cliente, String temporada, String modelo,
                               String nombreBolso, int colores, boolean conFoto) {
}

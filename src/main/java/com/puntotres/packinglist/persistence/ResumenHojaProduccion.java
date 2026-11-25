package com.puntotres.packinglist.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una Hoja de Producción guardada vista desde el listado de antiguas: sin el snapshot JSON. */
public record ResumenHojaProduccion(Long id, LocalDateTime fecha, String cliente, String temporada,
                                    String modelo, String nombreBolso, String taller, int numero, LocalDate fechaSalida) {
}

package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

/** Una hoja guardada vista desde el listado de antiguos: sin el snapshot JSON. */
public record ResumenHojaEmbalaje(Long id, LocalDateTime fecha, String cliente, String temporada, int numeroEnvio,
                                  String referencia, String codigoColor, String taller) {
}

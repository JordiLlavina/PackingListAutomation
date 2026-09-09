package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

/**
 * Una temporada guardada SIN su excel: lo que hace falta para pintarla en un
 * desplegable o en una tabla.
 *
 * Va aparte de la entidad a propósito. El desplegable de la pantalla de
 * entrada se pinta en cada carga, y con la entidad entera cada carga se
 * traería del disco todos los excels de pedido de todos los clientes —varios
 * megas— para enseñar cuatro nombres.
 */
public record ResumenTemporada(Long id, String cliente, String temporada,
                               String nombreFichero, LocalDateTime fechaActualizacion) {
}

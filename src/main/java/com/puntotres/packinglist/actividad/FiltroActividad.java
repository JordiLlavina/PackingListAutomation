package com.puntotres.packinglist.actividad;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.puntotres.packinglist.seguridad.Seccion;

/**
 * Lo que se pide al dashboard. Las fechas son días, no instantes: quien mira
 * la pantalla piensa en "del 1 al 15", no en husos.
 */
public record FiltroActividad(LocalDate desde, LocalDate hasta, Long usuarioId, Seccion seccion) {

    public static FiltroActividad ultimos30Dias() {
        return new FiltroActividad(LocalDate.now().minusDays(30), LocalDate.now(), null, null);
    }

    public OffsetDateTime desdeInstante() {
        return desde.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }

    /**
     * El día siguiente a las cero horas: "hasta el 15" incluye todo el 15.
     * Con la fecha a secas se quedaría en la medianoche del 15 y dejaría
     * fuera la jornada entera.
     */
    public OffsetDateTime hastaInstante() {
        return hasta.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }
}

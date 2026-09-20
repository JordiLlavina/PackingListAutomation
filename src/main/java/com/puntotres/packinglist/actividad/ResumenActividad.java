package com.puntotres.packinglist.actividad;

import java.time.OffsetDateTime;
import java.util.List;

import com.puntotres.packinglist.seguridad.Seccion;

/** Lo que pinta el dashboard para un filtro. */
public record ResumenActividad(
        long personas,
        long accesos,
        long documentos,
        long envios,
        List<LineaUsuario> porUsuario,
        List<LineaSeccion> porSeccion,
        List<LineaCliente> porCliente,
        List<EventoActividad> cronologia) {

    public record LineaUsuario(String correo, OffsetDateTime ultimoAcceso,
                               long accesos, long documentos) {
    }

    public record LineaSeccion(Seccion seccion, long acciones) {

        /**
         * El ancho de la barra en tramos de cinco, para la clase barra-N del
         * CSS. No se escribe como style en línea porque eso obligaría a abrir
         * la CSP con unsafe-inline.
         */
        public int tramo(long maximo) {
            if (maximo <= 0) {
                return 0;
            }
            long porcentaje = Math.round(acciones * 100.0 / maximo);
            return (int) (Math.max(5, Math.min(100, porcentaje)) / 5 * 5);
        }
    }

    public record LineaCliente(String cliente, long documentos, long acciones) {
    }
}

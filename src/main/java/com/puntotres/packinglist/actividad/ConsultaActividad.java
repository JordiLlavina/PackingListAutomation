package com.puntotres.packinglist.actividad;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.seguridad.Seccion;

/** Lo que el dashboard le pregunta a la tabla de eventos. */
@Service
public class ConsultaActividad {

    /** Lo que cabe leer de una sentada; más es ruido. */
    public static final int EVENTOS_EN_LA_CRONOLOGIA = 100;

    private final EventoActividadRepository eventos;

    public ConsultaActividad(EventoActividadRepository eventos) {
        this.eventos = eventos;
    }

    public ResumenActividad resumir(FiltroActividad filtro) {
        OffsetDateTime desde = filtro.desdeInstante();
        OffsetDateTime hasta = filtro.hastaInstante();
        Long usuario = filtro.usuarioId();
        Seccion seccion = filtro.seccion();

        long documentos = 0;
        long envios = 0;
        for (Object[] fila : eventos.agruparPorTipo(desde, hasta, usuario, seccion)) {
            TipoEvento tipo = (TipoEvento) fila[0];
            long cantidad = ((Number) fila[1]).longValue();
            long veces = ((Number) fila[2]).longValue();
            if (tipo.produceDocumentos()) {
                documentos += cantidad;
            }
            if (tipo == TipoEvento.ENVIO_IMPORTADO) {
                // Las VECES, no la suma de cantidades: la cantidad de un
                // envío importado es su número de cajas.
                envios += veces;
            }
        }

        return new ResumenActividad(
                eventos.contarPersonas(desde, hasta, usuario, seccion),
                eventos.contarAccesos(desde, hasta, usuario, seccion),
                documentos,
                envios,
                lineasDeUsuario(desde, hasta, usuario, seccion),
                lineasDeSeccion(desde, hasta, usuario, seccion),
                lineasDeCliente(desde, hasta, usuario, seccion),
                eventos.cronologia(desde, hasta, usuario, seccion,
                        PageRequest.of(0, EVENTOS_EN_LA_CRONOLOGIA)));
    }

    private List<ResumenActividad.LineaUsuario> lineasDeUsuario(
            OffsetDateTime desde, OffsetDateTime hasta, Long usuario, Seccion seccion) {
        List<ResumenActividad.LineaUsuario> lineas = new ArrayList<>();
        for (Object[] fila : eventos.agruparPorUsuario(desde, hasta, usuario, seccion)) {
            lineas.add(new ResumenActividad.LineaUsuario(
                    (String) fila[0],
                    (OffsetDateTime) fila[1],
                    ((Number) fila[2]).longValue(),
                    ((Number) fila[3]).longValue()));
        }
        return lineas;
    }

    private List<ResumenActividad.LineaSeccion> lineasDeSeccion(
            OffsetDateTime desde, OffsetDateTime hasta, Long usuario, Seccion seccion) {
        List<ResumenActividad.LineaSeccion> lineas = new ArrayList<>();
        for (Object[] fila : eventos.agruparPorSeccion(desde, hasta, usuario, seccion)) {
            lineas.add(new ResumenActividad.LineaSeccion(
                    (Seccion) fila[0], ((Number) fila[1]).longValue()));
        }
        return lineas;
    }

    private List<ResumenActividad.LineaCliente> lineasDeCliente(
            OffsetDateTime desde, OffsetDateTime hasta, Long usuario, Seccion seccion) {
        List<ResumenActividad.LineaCliente> lineas = new ArrayList<>();
        for (Object[] fila : eventos.agruparPorCliente(desde, hasta, usuario, seccion)) {
            lineas.add(new ResumenActividad.LineaCliente(
                    (String) fila[0],
                    ((Number) fila[1]).longValue(),
                    ((Number) fila[2]).longValue()));
        }
        return lineas;
    }
}

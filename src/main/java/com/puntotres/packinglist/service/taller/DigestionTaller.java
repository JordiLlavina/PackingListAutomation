package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que se enseña en la pantalla de ajuste: las referencias que ha mandado el
 * taller, ya cruzadas con el pedido del cliente y con lo que el programa
 * recuerda de envíos anteriores.
 *
 * Los avisos se leen y se sigue; los bloqueos impiden generar. La diferencia
 * es si el usuario puede arreglarlo tecleando en esta misma pantalla.
 */
public class DigestionTaller {

    private final List<GrupoReferencia> grupos = new ArrayList<>();
    private final List<String> destinosActivos = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();
    private final List<String> bloqueos = new ArrayList<>();

    public List<GrupoReferencia> getGrupos() {
        return grupos;
    }

    /** Las destinaciones que salen del pedido, en orden de primera aparición. */
    public List<String> getDestinosActivos() {
        return destinosActivos;
    }

    public List<String> getAvisos() {
        return avisos;
    }

    public List<String> getBloqueos() {
        return bloqueos;
    }

    public boolean sePuedeGenerar() {
        return bloqueos.isEmpty()
                && grupos.stream().noneMatch(GrupoReferencia::estaPendiente);
    }

    /** Las referencias a las que todavía les falta el dato que impide generar. */
    public List<String> referenciasPendientes() {
        return grupos.stream()
                .filter(GrupoReferencia::estaPendiente)
                .map(GrupoReferencia::getReferencia)
                .toList();
    }

    /** Lo que entra en el algoritmo: una fila por artículo, ya ajustada. */
    public List<FilaAjustada> aFilasAjustadas() {
        List<FilaAjustada> filas = new ArrayList<>();
        for (GrupoReferencia grupo : grupos) {
            for (FilaDigerida fila : grupo.getFilas()) {
                filas.add(new FilaAjustada(grupo.getReferencia(), fila.getColor(),
                        fila.getTalla(), fila.getRecibido(), grupo.getMedidaCaja(),
                        grupo.getUnidadesPorCaja(), fila.getObjetivos()));
            }
        }
        return filas;
    }
}

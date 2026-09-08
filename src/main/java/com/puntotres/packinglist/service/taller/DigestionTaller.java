package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * Avisos y bloqueos SIN repetidos, y en el orden en que aparecen.
     *
     * Una referencia puede ocupar seis filas de la hoja del taller, y sin esto
     * su aviso saldría seis veces: la lista se vuelve ilegible justo cuando
     * más hay que leerla, y quien la mira acaba dándola por ruido. El mismo
     * mensaje dos veces no añade nada.
     */
    private final Set<String> avisos = new LinkedHashSet<>();
    private final Set<String> bloqueos = new LinkedHashSet<>();

    public List<GrupoReferencia> getGrupos() {
        return grupos;
    }

    /** Las destinaciones que salen del pedido, en orden de primera aparición. */
    public List<String> getDestinosActivos() {
        return destinosActivos;
    }

    public Collection<String> getAvisos() {
        return avisos;
    }

    public Collection<String> getBloqueos() {
        return bloqueos;
    }

    public boolean sePuedeGenerar() {
        return bloqueos.isEmpty()
                && grupos.stream().noneMatch(GrupoReferencia::estaPendiente)
                && repartosImposibles().isEmpty();
    }

    /** Las referencias a las que todavía les falta el dato que impide generar. */
    public List<String> referenciasPendientes() {
        return grupos.stream()
                .filter(GrupoReferencia::estaPendiente)
                .map(GrupoReferencia::getReferencia)
                .toList();
    }

    /**
     * Las filas donde se está repartiendo más género del que ha llegado.
     *
     * Bloquea, no avisa: no se puede empaquetar lo que no está en el almacén.
     * Dejar pasar el envío no lo arreglaría —el reparto recorta por prioridad
     * y sirve de menos a las destinaciones de abajo—, así que el packing
     * saldría plausible y con menos unidades de las que dice esta pantalla, y
     * nadie lo vería hasta comparar los dos documentos.
     *
     * Se calcula cada vez y no se guarda en {@code bloqueos} porque depende de
     * lo que el usuario acaba de teclear: un bloqueo fijo seguiría ahí después
     * de corregirlo.
     */
    public List<String> repartosImposibles() {
        List<String> imposibles = new ArrayList<>();
        for (GrupoReferencia grupo : grupos) {
            for (FilaDigerida fila : grupo.getFilas()) {
                if (fila.repartoImposible()) {
                    imposibles.add("De " + grupo.getReferencia() + " " + fila.getColor()
                            + tallaDe(fila) + " han llegado " + fila.getRecibido()
                            + " unidades y se están repartiendo " + fila.totalObjetivo()
                            + ": no se puede enviar más de lo que ha llegado del taller");
                }
            }
        }
        return imposibles;
    }

    private static String tallaDe(FilaDigerida fila) {
        return "U".equals(fila.getTalla()) ? "" : " talla " + fila.getTalla();
    }

    /** Lo que entra en el algoritmo: una fila por artículo, ya ajustada. */
    public List<FilaAjustada> aFilasAjustadas() {
        List<FilaAjustada> filas = new ArrayList<>();
        for (GrupoReferencia grupo : grupos) {
            for (FilaDigerida fila : grupo.getFilas()) {
                filas.add(new FilaAjustada(grupo.getReferencia(), fila.getColor(),
                        fila.getTalla(), fila.getRecibido(), grupo.getMedidaCaja(),
                        grupo.getUnidadesPorCaja(), grupo.getPesoBrutoKg(),
                        fila.getObjetivos()));
            }
        }
        return filas;
    }
}

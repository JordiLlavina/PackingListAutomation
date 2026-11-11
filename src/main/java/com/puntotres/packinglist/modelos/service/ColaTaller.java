package com.puntotres.packinglist.modelos.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.modelos.persistence.Direccion;
import com.puntotres.packinglist.modelos.persistence.EstadoModificacion;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;

/**
 * La cola de órdenes del taller: las órdenes del despacho al taller que siguen
 * abiertas (NUEVA, VISTA, EN_CURSO), una detrás de otra, para que se sepa cuál
 * toca después. Sustituye a las prioridades alta / normal / baja, que cambiaban
 * demasiado para decir nada. Los avisos del taller no entran: los aplica el
 * despacho.
 *
 * El orden es {@code modificacion.posicion_cola} y, a igualdad (o sin número),
 * la más antigua antes. Cada cambio vuelve a numerar la cola entera de 1 a N:
 * son decenas, no miles. Una orden que se cierra conserva su número, que ya
 * no cuenta porque la cola son solo las abiertas.
 *
 * Las reglas de quién puede qué son de {@link Modificaciones}; aquí solo se
 * ordena.
 */
@Service
public class ColaTaller {

    /** Las que siguen en la cola. */
    static final Set<EstadoModificacion> ABIERTAS = EnumSet.of(EstadoModificacion.NUEVA, EstadoModificacion.VISTA,
            EstadoModificacion.EN_CURSO);

    /** Posición; a igualdad (o sin ella, al final) la más antigua; y el id para que nunca empaten. */
    static final Comparator<Modificacion> ORDEN = Comparator
            .comparing(Modificacion::getPosicionCola, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Modificacion::getCreadaEn, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Modificacion::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ModificacionRepository repositorio;

    public ColaTaller(ModificacionRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Si la modificación está en la cola: una orden para el taller que sigue abierta. */
    public static boolean enLaCola(Modificacion m) {
        return m != null && !m.esAviso() && m.getEstado() != null && ABIERTAS.contains(m.getEstado());
    }

    /** Las órdenes abiertas, de la primera a la última. */
    @Transactional(readOnly = true)
    public List<Modificacion> cola() {
        List<Modificacion> lista = new ArrayList<>(
                repositorio.findByDireccionAndEstadoIn(Direccion.DESPACHO_A_TALLER, ABIERTAS));
        lista.sort(ORDEN);
        return lista;
    }

    /** El puesto de cada orden de la cola, empezando por 1. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> posiciones() {
        Map<Long, Integer> puestos = new HashMap<>();
        List<Modificacion> lista = cola();
        for (int i = 0; i < lista.size(); i++) {
            puestos.put(lista.get(i).getId(), i + 1);
        }
        return puestos;
    }

    /**
     * Mete en la cola una orden recién creada: delante de {@code antesDe} o,
     * sin ella, al final. Una {@code antesDe} que ya no está en la cola (se ha
     * hecho o descartado mientras se escribía la orden) es un 400: ponerla al
     * final en silencio la dejaría donde nadie ha dicho.
     */
    @Transactional
    public void colocar(Modificacion nueva, Long antesDe) {
        List<Modificacion> lista = sinElla(nueva);
        int indice = lista.size();
        if (antesDe != null) {
            indice = indiceDe(lista, antesDe);
            if (indice < 0) {
                throw new IllegalArgumentException(
                        "La orden delante de la que querías ponerla ya no está en la cola: elige otra posición");
            }
        }
        lista.add(indice, nueva);
        numerar(lista);
    }

    /** La pone la primera de la cola (al marcarla urgente). */
    @Transactional
    public void alPrincipio(Modificacion orden) {
        List<Modificacion> lista = sinElla(orden);
        lista.add(0, orden);
        numerar(lista);
    }

    /**
     * Deja la cola en el orden de {@code ids}, que tiene que ser la cola
     * entera: si falta alguna o sobra alguna (otra persona ha creado o cerrado
     * una mientras tanto), {@link ColaCambiada} y no se toca nada. Así un
     * reordenado nunca se hace sobre una cola que no es la que se veía.
     */
    @Transactional
    public void reordenar(List<Long> ids) {
        List<Modificacion> lista = cola();
        if (ids == null || ids.size() != lista.size() || new HashSet<>(ids).size() != ids.size()) {
            throw new ColaCambiada();
        }
        Map<Long, Modificacion> porId = new HashMap<>();
        lista.forEach(m -> porId.put(m.getId(), m));
        List<Modificacion> nueva = new ArrayList<>();
        for (Long id : ids) {
            Modificacion m = porId.get(id);
            if (m == null) {
                throw new ColaCambiada();
            }
            nueva.add(m);
        }
        numerar(nueva);
    }

    private List<Modificacion> sinElla(Modificacion orden) {
        List<Modificacion> lista = cola();
        lista.removeIf(m -> m.getId() != null && m.getId().equals(orden.getId()));
        return lista;
    }

    private static int indiceDe(Collection<Modificacion> lista, Long id) {
        int i = 0;
        for (Modificacion m : lista) {
            if (id.equals(m.getId())) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static void numerar(List<Modificacion> lista) {
        for (int i = 0; i < lista.size(); i++) {
            lista.get(i).ponerPosicionCola(i + 1);
        }
    }

    /** La cola ya no es la que se veía al reordenar: hay que volver a cargarla (un 409). */
    public static class ColaCambiada extends RuntimeException {

        public ColaCambiada() {
            super("La cola ha cambiado mientras la ordenabas (alguien ha creado o cerrado una orden). "
                    + "Se ha vuelto a cargar: repite el cambio.");
        }
    }
}

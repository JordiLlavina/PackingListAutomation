package com.puntotres.packinglist.web;

import java.util.List;

import com.puntotres.packinglist.model.CajaData;

/**
 * Una fila de la tabla de revisión. Puede representar UNA caja o un tramo de
 * cajas consecutivas equivalentes compactadas por {@link AgrupadorFilasRevision}.
 *
 * <ul>
 * <li>{@code indiceGlobal}: nombra los inputs del formulario
 *     ({@code pesos[indiceGlobal].*}); es único en toda la página.</li>
 * <li>{@code rangoCajas}: lo que se pinta en la columna CAJA — {@code "4-8"}
 *     para un tramo, {@code "4"} para una caja suelta.</li>
 * <li>{@code indicesEnDestino}: la posición de CADA caja representada por la
 *     fila dentro de la lista de su destinación. El peso tecleado se aplica a
 *     todas ellas. Se localiza por posición y no por número de caja porque el
 *     número puede repetirse (caja mixta).</li>
 * <li>{@code caja}: la caja líder de la fila, de la que salen todos los valores
 *     mostrados. En una fila compactada las demás cajas son equivalentes en
 *     todo lo visible, así que da igual cuál se lea.</li>
 * <li>{@code esLider}: es la primera línea de su caja física, la única que
 *     muestra campos de peso editables.</li>
 * <li>{@code bultoMixto}: la caja física de la fila tiene más de una línea
 *     (varios artículos o varias tallas en el mismo bulto).</li>
 * <li>{@code cajaPendiente}: la caja física aún no tiene los dos pesos, para
 *     resaltar la fila.</li>
 * <li>{@code alternable}: la fila pinta el triángulo de desplegar/plegar. Solo
 *     lo lleva la fila compactada (▶) y la PRIMERA fila de un grupo ya
 *     desplegado (▼); las demás filas del grupo y las cajas sueltas, no. El
 *     submit apunta al índice de inicio del grupo, que en ambos casos es
 *     {@code indicesEnDestino.get(0)}.</li>
 * <li>{@code desplegada}: la fila viene de un grupo desplegado, o sea que el
 *     triángulo mira hacia abajo y pulsarlo vuelve a plegar.</li>
 * <li>{@code bandaPalet}: qué matiz de fondo le toca a la fila para que
 *     el cambio de palet se vea sin leer la columna. Es el orden de aparición
 *     del valor de palet en la destinación, cíclico sobre la paleta del CSS;
 *     {@link #SIN_BANDA} cuando la caja no trae palet.</li>
 * </ul>
 */
public record FilaCaja(int indiceGlobal, String rangoCajas, List<Integer> indicesEnDestino,
                       CajaData caja, boolean esLider, boolean bultoMixto, boolean cajaPendiente,
                       boolean alternable, boolean desplegada, int bandaPalet) {

    /** Una caja sin palet no se tiñe: su hueco vacío ya se señala aparte. */
    public static final int SIN_BANDA = -1;

    /**
     * La clase CSS que pinta el fondo de la fila ({@code palet-3}), o cadena
     * vacía si la caja no trae palet. El color concreto vive en el CSS: aquí
     * solo se decide QUÉ filas comparten matiz.
     */
    public String claseBandaPalet() {
        return bandaPalet == SIN_BANDA ? "" : "palet-" + bandaPalet;
    }

    /**
     * El número de caja solo se edita cuando la fila representa UNA caja: en un
     * rango compactado ("4-8") no hay un número que teclear, hay que desplegarlo.
     */
    public boolean numeroCajaEditable() {
        return indicesEnDestino.size() == 1;
    }

    /**
     * Si la fila ofrece el botón de recalcular peso, que propaga el peso
     * tecleado al resto de cajas del mismo modelo.
     *
     * Un bulto mixto no lo ofrece: su peso es el de varios artículos juntos y
     * no se sabe qué parte es de cada uno, así que no hay peso por unidad que
     * llevarse a otras cajas. El peso se sigue editando a mano ({@code
     * esLider}); lo que se retira es la promesa de que ese número sirva para
     * calcular los demás.
     */
    public boolean puedeRecalcularPeso() {
        return esLider && !bultoMixto;
    }
}

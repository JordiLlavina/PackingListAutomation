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
 * <li>{@code cajaPendiente}: la caja física aún no tiene los dos pesos, para
 *     resaltar la fila.</li>
 * </ul>
 */
public record FilaCaja(int indiceGlobal, String rangoCajas, List<Integer> indicesEnDestino,
                       CajaData caja, boolean esLider, boolean cajaPendiente) {
}

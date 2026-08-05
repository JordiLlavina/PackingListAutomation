package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.puntotres.packinglist.model.CajaData;

/**
 * Compacta la tabla de la pantalla de revisión: un tramo de cajas consecutivas
 * equivalentes se pinta en una sola fila con el rango de números de caja
 * ({@code 4-8}) en vez de en cinco filas casi idénticas.
 *
 * Es una transformación de VISTA: las {@code CajaData} de la sesión siguen
 * siendo una por caja física y ni el dominio ni los builders de Excel se
 * enteran.
 *
 * La regla que gobierna el criterio de agrupación: <b>una fila compactada nunca
 * muestra un valor que no sea cierto para todas sus cajas</b>. Cualquier
 * diferencia parte el grupo en vez de esconderse bajo el rango.
 */
public final class AgrupadorFilasRevision {

    private AgrupadorFilasRevision() {
    }

    /** Agrupa sin ninguna fila desplegada. */
    public static List<FilaCaja> agrupar(List<CajaData> cajas, int primerIndiceGlobal) {
        return agrupar(cajas, primerIndiceGlobal, Set.of());
    }

    /**
     * Agrupa las cajas de UNA destinación, en su orden de la lista.
     *
     * @param cajas             las líneas de la destinación (una caja física
     *                          puede ocupar varias)
     * @param primerIndiceGlobal índice con el que arranca a numerar los inputs
     *                          del formulario: es único en toda la página, así
     *                          que cada destinación sigue donde acabó la anterior
     * @param indicesDesplegados posiciones de arranque de los grupos que el
     *                          usuario ha desplegado a mano: en vez de una fila
     *                          compactada se emite una fila por caja. La marca
     *                          es posicional y se ignora sin más si ahí ya no
     *                          arranca ningún grupo, porque los datos pueden
     *                          haber cambiado desde que se puso
     */
    public static List<FilaCaja> agrupar(List<CajaData> cajas, int primerIndiceGlobal,
                                         Set<Integer> indicesDesplegados) {
        // Una caja física son todas las líneas con el mismo numeroCaja dentro
        // de la destinación, y solo la primera —la líder— lleva su peso
        // (ver ARCHITECTURE.md, "un peso por caja física").
        Map<Integer, CajaData> liderPorCaja = new LinkedHashMap<>();
        Map<Integer, Integer> lineasPorCaja = new HashMap<>();
        for (CajaData caja : cajas) {
            liderPorCaja.putIfAbsent(caja.getNumeroCaja(), caja);
            lineasPorCaja.merge(caja.getNumeroCaja(), 1, Integer::sum);
        }

        List<FilaCaja> filas = new ArrayList<>();
        int indiceGlobal = primerIndiceGlobal;
        int i = 0;
        while (i < cajas.size()) {
            CajaData primera = cajas.get(i);
            List<Integer> indices = new ArrayList<>();
            indices.add(i);
            int ultimoNumero = primera.getNumeroCaja();

            // Las cajas mixtas no se compactan: se siguen pintando línea a
            // línea, que es como se revisan a mano.
            if (esDeUnaSolaLinea(primera, lineasPorCaja)) {
                int j = i + 1;
                while (j < cajas.size()
                        && esDeUnaSolaLinea(cajas.get(j), lineasPorCaja)
                        // Correlativas: sin esto un "4-8" podría estar tapando
                        // que las cajas 6 y 7 fueron a otro sitio.
                        && cajas.get(j).getNumeroCaja() == ultimoNumero + 1
                        && sonEquivalentes(primera, cajas.get(j))) {
                    indices.add(j);
                    ultimoNumero = cajas.get(j).getNumeroCaja();
                    j++;
                }
                i = j;
            } else {
                i++;
            }

            // Un grupo de más de una caja se puede desplegar; si el usuario ya
            // lo desplegó, se emite una fila por caja. Solo entran en un grupo
            // cajas de una línea, así que cada fila desplegada es la líder de
            // su propia caja física (y nunca un bulto mixto): edita sus dos
            // pesos y ofrece el recálculo.
            boolean grupo = indices.size() > 1;
            if (grupo && indicesDesplegados.contains(indices.get(0))) {
                for (int k = 0; k < indices.size(); k++) {
                    CajaData suelta = cajas.get(indices.get(k));
                    filas.add(new FilaCaja(indiceGlobal++,
                            String.valueOf(suelta.getNumeroCaja()), List.of(indices.get(k)),
                            suelta, true, false, !suelta.tienePesosCompletos(), k == 0, true));
                }
                continue;
            }

            CajaData lider = liderPorCaja.get(primera.getNumeroCaja());
            filas.add(new FilaCaja(indiceGlobal++,
                    rango(primera.getNumeroCaja(), ultimoNumero), indices, primera,
                    lider == primera, !esDeUnaSolaLinea(primera, lineasPorCaja),
                    !lider.tienePesosCompletos(), grupo, false));
        }
        return filas;
    }

    private static boolean esDeUnaSolaLinea(CajaData caja, Map<Integer, Integer> lineasPorCaja) {
        return lineasPorCaja.get(caja.getNumeroCaja()) == 1;
    }

    /**
     * Dos cajas son equivalentes si coinciden en la clave pedida (referencia,
     * color, product order y cantidad) y además en todo lo que la fila muestra
     * —tamaño, palet y los dos pesos— o implica: la talla no es columna
     * visible, pero dos cinturones de la misma referencia y distinta talla no
     * pueden esconderse bajo un mismo rango porque la talla de la línea líder
     * es la que acaba en la etiqueta de la caja.
     */
    private static boolean sonEquivalentes(CajaData a, CajaData b) {
        return Objects.equals(a.getReferencia(), b.getReferencia())
                && Objects.equals(a.getCodigoColor(), b.getCodigoColor())
                && Objects.equals(a.getNumeroPedido(), b.getNumeroPedido())
                && a.getCantidad() == b.getCantidad()
                && Objects.equals(a.getTamanoCaja(), b.getTamanoCaja())
                && Objects.equals(a.getNumeroPalet(), b.getNumeroPalet())
                && Objects.equals(a.getPesoNetoKg(), b.getPesoNetoKg())
                && Objects.equals(a.getPesoBrutoKg(), b.getPesoBrutoKg())
                && Objects.equals(a.getTalla(), b.getTalla());
    }

    /** "4-8" para un tramo; "4" a secas para una caja suelta, nunca "4-4". */
    private static String rango(int primera, int ultima) {
        return primera == ultima ? String.valueOf(primera) : primera + "-" + ultima;
    }
}

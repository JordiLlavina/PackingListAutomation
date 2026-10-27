package com.puntotres.packinglist.service.corte;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.puntotres.packinglist.persistence.MemoriaPieles;

/**
 * Lo que sale del excel de producción de Puntotres: las referencias con los
 * bolsos que hay que cortar de cada color (la cantidad del taller), y lo que
 * ese excel ya dice de las pieles, que rellena la tabla de pieles en vez de
 * la memoria.
 *
 * {@code pielDe}: referencia → nombre de la piel, el primero escrito en sus
 * filas. {@code coloresDe}: referencia → color del bolso (su código, o su
 * descripción si no lo tiene, como los agrupa {@link PedidoCorte}) → color de
 * cada piel, la principal primero y detrás los que separa la barra.
 */
public record ProduccionCorte(PedidoCorte pedido, Map<String, String> pielDe,
                              Map<String, Map<String, List<String>>> coloresDe, List<String> avisos) {

    public ProduccionCorte {
        pielDe = Map.copyOf(pielDe);
        coloresDe = Map.copyOf(coloresDe);
        avisos = List.copyOf(avisos);
    }

    /**
     * Pone en las filas la piel y sus colores. Lo que va detrás de una barra
     * es otra piel del mismo bolso: una combinación, con el nombre de la
     * piel de la fila (el bicolor "ALPACA BICOLOR" es la misma piel en dos
     * colores), que en la tabla se puede cambiar. El forro no lo trae el
     * excel y se queda vacío.
     */
    public void rellenar(List<FilaCorte> filas) {
        for (FilaCorte fila : filas) {
            String referencia = fila.referencia().referencia();
            String piel = pielDe.getOrDefault(referencia, "");
            fila.setNombrePiel(piel);
            Map<String, List<String>> deLaReferencia = coloresDe.getOrDefault(referencia, Map.of());
            List<ColorCorte> colores = fila.colores();
            int combinaciones = 0;
            for (int indice = 0; indice < colores.size(); indice++) {
                List<String> deLasPieles = deLaReferencia.getOrDefault(claveDe(colores.get(indice)), List.of());
                if (deLasPieles.isEmpty()) {
                    continue;
                }
                fila.setColorPiel(indice, deLasPieles.get(0));
                List<String> deCombinacion = new ArrayList<>(deLasPieles.subList(1,
                        Math.min(deLasPieles.size(), MemoriaPieles.MAXIMO_COMBINACIONES + 1)));
                fila.setColoresCombinacion(indice, deCombinacion);
                combinaciones = Math.max(combinaciones, deCombinacion.size());
            }
            fila.setCombinaciones(Collections.nCopies(combinaciones, piel));
        }
    }

    /**
     * Como {@link PedidoCorte} agrupa los colores: por código, y sin él por
     * descripción; la prensa de un color, aparte.
     */
    static String claveDe(ColorCorte color) {
        return (color.prensa() ? "PRENSA " : "") + (color.codigo().isEmpty() ? color.descripcion() : color.codigo());
    }
}

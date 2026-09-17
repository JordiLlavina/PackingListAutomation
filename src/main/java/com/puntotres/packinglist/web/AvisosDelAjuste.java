package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.puntotres.packinglist.service.taller.AvisoTaller;

/**
 * Los avisos de la pantalla de ajuste, repartidos entre la lista general y la
 * tarjeta de cada referencia.
 *
 * La digestión los emite todos juntos, que es la verdad del envío. Pero la
 * pantalla tiene una tarjeta por referencia, y un aviso que habla de una de
 * ellas —"el pedido no tiene ninguna línea de ULL729 color 001"— se lee mucho
 * mejor encima de su tabla que en una lista de veinte líneas donde hay que ir
 * buscando a cuál toca. Repartirlos es decisión de pantalla, y por eso vive
 * aquí y no en la digestión, igual que {@code AgrupadorAvisosEtiquetas}.
 *
 * <p>Se reparte por la pieza {@code referencia} del aviso, nunca buscando el
 * código dentro de la frase: ver {@link AvisoTaller}.
 *
 * <p><b>Ningún aviso se pierde.</b> Uno cuya referencia no tenga tarjeta
 * —una fila apartada por ser de otro cliente, por ejemplo— cae a la lista
 * general. Sin esa regla desaparecería de la pantalla sin decir nada, que es
 * peor que enseñarlo donde no toca.
 */
public final class AvisosDelAjuste {

    private final List<String> generales;
    private final Map<String, List<String>> porReferencia;

    private AvisosDelAjuste(List<String> generales, Map<String, List<String>> porReferencia) {
        this.generales = List.copyOf(generales);
        this.porReferencia = Map.copyOf(porReferencia);
    }

    /**
     * @param avisos      los avisos de la digestión, en su orden
     * @param referencias las referencias que tienen tarjeta en la pantalla
     */
    public static AvisosDelAjuste repartir(Collection<AvisoTaller> avisos,
                                           Collection<String> referencias) {
        List<String> generales = new ArrayList<>();
        Map<String, List<String>> porReferencia = new LinkedHashMap<>();

        for (AvisoTaller aviso : avisos) {
            if (aviso.referencia() != null && referencias.contains(aviso.referencia())) {
                porReferencia.computeIfAbsent(aviso.referencia(), r -> new ArrayList<>())
                        .add(aviso.texto());
            } else {
                generales.add(aviso.texto());
            }
        }
        return new AvisosDelAjuste(generales, porReferencia);
    }

    /** Los del envío o del fichero entero, los de la sección de arriba. */
    public List<String> getGenerales() {
        return generales;
    }

    /**
     * Los de cada referencia, por su código. Una referencia sin avisos no
     * está en el mapa —la plantilla espera null—: con una lista vacía se
     * pintaría el hueco de la lista encima de cada tabla.
     */
    public Map<String, List<String>> getPorReferencia() {
        return porReferencia;
    }
}

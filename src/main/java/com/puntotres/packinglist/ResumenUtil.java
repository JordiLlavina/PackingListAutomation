package com.puntotres.packinglist;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formatea el resumen "N (desglose)" que usan los bloques de totales de
 * APC y la plantilla genérica para PALLETS/CARTONS, p. ej.:
 * "2 (80x120x130cm)" (un solo valor) o
 * "24 (2*60x40x30cm+22*60x40x40cm)" (varios valores distintos).
 */
public final class ResumenUtil {

    private ResumenUtil() {
    }

    public static String resumenConteo(List<String> valores) {
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (String valor : valores) {
            conteo.merge(valor, 1, Integer::sum);
        }
        String desglose = (conteo.size() == 1)
                ? conteo.keySet().iterator().next() + "cm"
                : conteo.entrySet().stream()
                        .map(e -> e.getValue() + "*" + e.getKey() + "cm")
                        .collect(Collectors.joining("+"));
        return valores.size() + " (" + desglose + ")";
    }
}

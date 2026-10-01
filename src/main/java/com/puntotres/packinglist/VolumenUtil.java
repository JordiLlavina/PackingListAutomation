package com.puntotres.packinglist;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cálculo de volumen en m3 a partir de medidas "LxWxH" en centímetros
 * (p. ej. "60x40x30"), compartido por los builders que necesitan sumar
 * volúmenes de cajas o palets (AMI, APC, genérico).
 */
public final class VolumenUtil {

    private VolumenUtil() {
    }

    public static double volumenM3(String medidasCm) {
        double volumen = 1;
        for (String parte : lados(medidasCm)) {
            volumen *= Double.parseDouble(parte.trim()) / 100.0;
        }
        return volumen;
    }

    /**
     * Suma de los volúmenes conocidos: una medida que FALTA (null o en
     * blanco) no suma en vez de tumbar la generación entera.
     *
     * Que falte es un caso real —la medida se escribe una sola vez para un
     * grupo de cajas, a veces de lado en el margen, y la extracción por
     * hojas no siempre la encuentra— y es de los que un humano resuelve en
     * la pantalla de revisión: el packing list se genera igual, con el
     * volumen de lo que sí está medido. Una medida PRESENTE pero ilegible
     * sigue lanzando: ahí no hay nada que interpretar.
     */
    public static double volumenTotalM3(List<String> medidasCm) {
        double total = 0;
        for (String medidas : medidasCm) {
            if (tieneMedida(medidas)) {
                total += volumenM3(medidas);
            }
        }
        return total;
    }

    /**
     * El mismo volumen que {@link #volumenTotalM3}, pero como FÓRMULA de Excel
     * (sin el "=") y no como resultado: el packing list enseña de dónde sale
     * el número y quien lo abre puede corregir una caja sin recalcular nada a
     * mano. Un término por medida distinta, con cuántas cajas la llevan y sus
     * tres lados en metros, en el orden en que aparecen:
     * {@code "2*0.6*0.4*0.3+51*0.6*0.4*0.4"}. Una sola caja de una medida va
     * sin el "1*".
     *
     * Van los lados y no el volumen de cada caja ya multiplicado porque el
     * desglose de cartones del mismo excel se escribe en centímetros
     * ("60x40x30cm"), y así los dos se leen uno al lado del otro.
     *
     * Mismo criterio que la suma: una medida que falta no suma y una ilegible
     * lanza. null si no hay ninguna medida que sumar, para que quien llama
     * escriba un cero en vez de una fórmula vacía.
     */
    public static String formulaVolumenM3(List<String> medidasCm) {
        Map<String, Integer> cajasPorMedida = new LinkedHashMap<>();
        for (String medidas : medidasCm) {
            if (tieneMedida(medidas)) {
                cajasPorMedida.merge(ladosEnMetros(medidas), 1, Integer::sum);
            }
        }
        if (cajasPorMedida.isEmpty()) {
            return null;
        }
        return cajasPorMedida.entrySet().stream()
                .map(e -> e.getValue() == 1 ? e.getKey() : e.getValue() + "*" + e.getKey())
                .collect(Collectors.joining("+"));
    }

    public static boolean tieneMedida(String medidasCm) {
        return medidasCm != null && !medidasCm.isBlank();
    }

    /**
     * La medida tal como se rotula en el desglose de un excel de cliente:
     * "?" cuando falta. Sin esto la celda saldría con un "null" que el
     * cliente leería como una medida más.
     */
    public static String etiqueta(String medidasCm) {
        return tieneMedida(medidasCm) ? medidasCm : "?";
    }

    /** "60x40x30" -> "0.6*0.4*0.3": los tres lados en metros, sin ceros de sobra. */
    private static String ladosEnMetros(String medidasCm) {
        return Arrays.stream(lados(medidasCm))
                .map(lado -> new BigDecimal(lado.trim()).movePointLeft(2)
                        .stripTrailingZeros().toPlainString())
                .collect(Collectors.joining("*"));
    }

    private static String[] lados(String medidasCm) {
        String[] partes = medidasCm == null ? new String[0] : medidasCm.split("[xX]");
        if (partes.length != 3) {
            throw new IllegalArgumentException(
                    "Las medidas deben tener formato LxWxH en cm, recibido: " + medidasCm);
        }
        return partes;
    }
}

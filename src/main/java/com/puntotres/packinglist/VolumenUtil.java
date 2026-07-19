package com.puntotres.packinglist;

import java.util.List;

/**
 * Cálculo de volumen en m3 a partir de medidas "LxWxH" en centímetros
 * (p. ej. "60x40x30"), compartido por los builders que necesitan sumar
 * volúmenes de cajas o palets (AMI, APC, genérico).
 */
public final class VolumenUtil {

    private VolumenUtil() {
    }

    public static double volumenM3(String medidasCm) {
        String[] partes = medidasCm.split("[xX]");
        if (partes.length != 3) {
            throw new IllegalArgumentException(
                    "Las medidas deben tener formato LxWxH en cm, recibido: " + medidasCm);
        }
        double volumen = 1;
        for (String parte : partes) {
            volumen *= Double.parseDouble(parte.trim()) / 100.0;
        }
        return volumen;
    }

    public static double volumenTotalM3(List<String> medidasCm) {
        double total = 0;
        for (String medidas : medidasCm) {
            total += volumenM3(medidas);
        }
        return total;
    }
}

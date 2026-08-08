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
        String[] partes = medidasCm == null ? new String[0] : medidasCm.split("[xX]");
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
}

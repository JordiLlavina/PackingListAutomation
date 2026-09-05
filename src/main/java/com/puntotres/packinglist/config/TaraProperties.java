package com.puntotres.packinglist.config;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tabla de taras (peso del embalaje vacío en kg) por tamaño de caja,
 * cargada desde application.yml:
 *
 * <pre>
 * packing-list:
 *   taras:
 *     "[60x40x40]": 1.6
 *     "[60x40x30]": 1.2
 * </pre>
 *
 * Añadir un tamaño nuevo = añadir una línea al yml, sin tocar Java.
 * Las claves se normalizan (minúsculas, sin espacios) para que
 * "60X40X40 " de una imagen case con "60x40x40" de la configuración.
 */
@ConfigurationProperties(prefix = "packing-list")
public class TaraProperties implements CatalogoTaras {

    private Map<String, Double> taras = new HashMap<>();

    public Map<String, Double> getTaras() {
        return taras;
    }

    public void setTaras(Map<String, Double> taras) {
        this.taras = new HashMap<>();
        taras.forEach((tamano, tara) -> this.taras.put(normalizar(tamano), tara));
    }

    @Override
    public Optional<Double> taraPara(String tamanoCaja) {
        if (tamanoCaja == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(taras.get(normalizar(tamanoCaja)));
    }

    /**
     * Un tamaño que no siga el patrón LxAxH no rompe el orden: se va al final,
     * y los empates se deshacen por nombre para que la lista sea siempre la
     * misma.
     */
    @Override
    public List<String> tamanosDeMayorAMenor() {
        return taras.keySet().stream()
                .sorted(Comparator.comparingLong(TaraProperties::volumen).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** Volumen en cm³ de un "LxAxH", o 0 si no se puede leer así. */
    private static long volumen(String tamano) {
        String[] partes = tamano.split("x");
        if (partes.length != 3) {
            return 0;
        }
        long total = 1;
        for (String parte : partes) {
            try {
                total *= Long.parseLong(parte.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return total;
    }

    private static String normalizar(String tamano) {
        return tamano.trim().toLowerCase().replace(" ", "");
    }
}

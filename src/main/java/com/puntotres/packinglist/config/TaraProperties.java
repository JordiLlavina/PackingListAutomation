package com.puntotres.packinglist.config;

import java.util.HashMap;
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
public class TaraProperties {

    private Map<String, Double> taras = new HashMap<>();

    public Map<String, Double> getTaras() {
        return taras;
    }

    public void setTaras(Map<String, Double> taras) {
        this.taras = new HashMap<>();
        taras.forEach((tamano, tara) -> this.taras.put(normalizar(tamano), tara));
    }

    /** Tara del tamaño de caja indicado, o vacío si no está en la tabla. */
    public Optional<Double> taraPara(String tamanoCaja) {
        if (tamanoCaja == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(taras.get(normalizar(tamanoCaja)));
    }

    private static String normalizar(String tamano) {
        return tamano.trim().toLowerCase().replace(" ", "");
    }
}

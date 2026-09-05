package com.puntotres.packinglist.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Taras cargadas desde application.yml:
 *
 * <pre>
 * packing-list:
 *   taras:
 *     "[60x40x40]": 1.6
 *     "[60x40x30]": 1.2
 * </pre>
 *
 * Es la SEMILLA del catálogo, no el catálogo: en la aplicación arrancada
 * manda la tabla de la base de datos ({@code CatalogoTarasJpa}), que se
 * siembra de aquí la primera vez y se corrige desde la pantalla /taras. Este
 * bloque sigue siendo lo que usan {@code Main.java} y los tests unitarios,
 * que no levantan contexto de Spring.
 *
 * Las claves se normalizan al enlazar, igual que al consultarlas.
 */
@ConfigurationProperties(prefix = "packing-list")
public class TaraProperties implements CatalogoTaras {

    private Map<String, Double> taras = new HashMap<>();

    public Map<String, Double> getTaras() {
        return taras;
    }

    public void setTaras(Map<String, Double> taras) {
        this.taras = new HashMap<>();
        taras.forEach((tamano, tara) -> this.taras.put(CatalogoTaras.normalizar(tamano), tara));
    }

    @Override
    public Optional<Double> taraPara(String tamanoCaja) {
        if (tamanoCaja == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(taras.get(CatalogoTaras.normalizar(tamanoCaja)));
    }

    @Override
    public List<String> tamanosDeMayorAMenor() {
        return CatalogoTaras.deMayorAMenor(taras.keySet());
    }
}

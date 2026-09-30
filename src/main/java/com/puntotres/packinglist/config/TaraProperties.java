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
 *   separadores-carton:
 *     por-caja: 2
 *     peso-kg: 0.08
 * </pre>
 *
 * De la tabla de cartones es la SEMILLA, no el catálogo: en la aplicación
 * arrancada manda la tabla de la base de datos ({@code CatalogoTarasJpa}), que
 * se siembra de aquí la primera vez y se corrige desde la pantalla /taras.
 * Este bloque sigue siendo lo que usan {@code Main.java} y los tests
 * unitarios, que no levantan contexto de Spring.
 *
 * De los separadores, en cambio, es el único origen: no son una tabla que
 * crezca con tamaños nuevos, son un número que vale para todas las cajas, así
 * que no tienen fila en la base de datos ni pantalla donde corregirse. Si
 * algún día hay que poder cambiarlo sin recompilar, va a la base de datos y a
 * /taras como fueron las taras.
 *
 * Las claves de la tabla se normalizan al enlazar, igual que al consultarlas.
 */
@ConfigurationProperties(prefix = "packing-list")
public class TaraProperties implements CatalogoTaras {

    private Map<String, Double> taras = new HashMap<>();

    private SeparadoresCarton separadoresCarton = new SeparadoresCarton();

    public Map<String, Double> getTaras() {
        return taras;
    }

    public void setTaras(Map<String, Double> taras) {
        this.taras = new HashMap<>();
        taras.forEach((tamano, tara) -> this.taras.put(CatalogoTaras.normalizar(tamano), tara));
    }

    public SeparadoresCarton getSeparadoresCarton() {
        return separadoresCarton;
    }

    public void setSeparadoresCarton(SeparadoresCarton separadoresCarton) {
        this.separadoresCarton = separadoresCarton != null
                ? separadoresCarton : new SeparadoresCarton();
    }

    @Override
    public Optional<Double> taraCartonPara(String tamanoCaja) {
        if (tamanoCaja == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(taras.get(CatalogoTaras.normalizar(tamanoCaja)));
    }

    @Override
    public double pesoSeparadoresKg() {
        return separadoresCarton.pesoTotalKg();
    }

    @Override
    public List<String> tamanosDeMayorAMenor() {
        return CatalogoTaras.deMayorAMenor(taras.keySet());
    }

    /**
     * Los separadores de cartón que van dentro de cada caja: cuántos son y lo
     * que pesa uno.
     *
     * Van los dos números por separado, y no el total ya multiplicado, porque
     * lo que se pesa en el almacén es UN separador: con el total, cambiar de
     * dos a tres obligaría a rehacer la multiplicación a mano y el yml dejaría
     * de decir de dónde sale el número.
     */
    public static class SeparadoresCarton {

        private int porCaja;

        private double pesoKg;

        public int getPorCaja() {
            return porCaja;
        }

        public void setPorCaja(int porCaja) {
            this.porCaja = porCaja;
        }

        public double getPesoKg() {
            return pesoKg;
        }

        public void setPesoKg(double pesoKg) {
            this.pesoKg = pesoKg;
        }

        /** Lo que pesan todos los separadores de una caja. */
        public double pesoTotalKg() {
            return porCaja * pesoKg;
        }
    }
}

package com.puntotres.packinglist.config;

import java.util.List;
import java.util.Optional;

/**
 * Tabla de taras (peso del cartón vacío en kg) por tamaño de caja.
 *
 * Existe como interfaz porque la tabla tiene dos orígenes: el bloque
 * {@code packing-list.taras} de application.yml, que hace de semilla y es lo
 * que usan {@code Main.java} y los tests unitarios —ninguno de los dos
 * levanta contexto de Spring—, y la tabla de la base de datos, que es la que
 * manda en la aplicación arrancada y la que edita la pantalla /taras.
 */
public interface CatalogoTaras {

    /** Tara del tamaño de caja indicado, o vacío si no está en la tabla. */
    Optional<Double> taraPara(String tamanoCaja);

    /**
     * Los tamaños conocidos, de la caja más grande a la más pequeña, para los
     * desplegables de la web. El orden es por VOLUMEN y no alfabético:
     * "100x40x40" es la caja más grande y como texto iría antes que
     * "40x30x20".
     */
    List<String> tamanosDeMayorAMenor();
}

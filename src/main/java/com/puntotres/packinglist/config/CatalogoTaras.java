package com.puntotres.packinglist.config;

import java.util.Collection;
import java.util.Comparator;
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
 *
 * La normalización de la clave y el orden del desplegable viven aquí, como
 * estáticos, porque son los mismos para los dos orígenes: si cada
 * implementación tuviera los suyos, un "60X40X40 " leído de un excel podría
 * encontrar tara con una y no con la otra.
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

    /**
     * Clave canónica de un tamaño de caja: minúsculas y sin espacios, para que
     * "60X40X40 " de una imagen case con "60x40x40" de la configuración.
     */
    static String normalizar(String tamano) {
        return tamano.trim().toLowerCase().replace(" ", "");
    }

    /**
     * Ordena tamaños ya normalizados de mayor a menor volumen. Un tamaño que
     * no siga el patrón LxAxH no rompe el orden: se va al final, y los
     * empates se deshacen por nombre para que la lista sea siempre la misma.
     */
    static List<String> deMayorAMenor(Collection<String> tamanos) {
        return tamanos.stream()
                .sorted(Comparator.comparingLong(CatalogoTaras::volumen).reversed()
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
}

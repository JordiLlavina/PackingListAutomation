package com.puntotres.packinglist.config;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.puntotres.packinglist.model.MedidaCaja;

/**
 * Tara (peso del embalaje vacío de una caja, en kg): el cartón de cada tamaño
 * más los separadores de cartón que van dentro de todas.
 *
 * Existe como interfaz porque la tabla de cartones tiene dos orígenes: el
 * bloque {@code packing-list.taras} de application.yml, que hace de semilla y
 * es lo que usan {@code Main.java} y los tests unitarios —ninguno de los dos
 * levanta contexto de Spring—, y la tabla de la base de datos, que es la que
 * manda en la aplicación arrancada y la que edita la pantalla /taras.
 *
 * La normalización de la clave, el orden del desplegable y la suma de los
 * separadores viven aquí, como estáticos o como default, porque son los
 * mismos para los dos orígenes: si cada implementación tuviera los suyos, un
 * "60X40X40 " leído de un excel podría encontrar tara con una y no con la
 * otra, y la misma caja pesaría distinto según por dónde se hubiera llegado.
 */
public interface CatalogoTaras {

    /**
     * Lo que pesa el CARTÓN vacío del tamaño indicado, o vacío si no está en
     * la tabla. Es el número que se pone en la báscula y se teclea en /taras,
     * y no es toda la tara de la caja: para eso está {@link #taraPara}.
     */
    Optional<Double> taraCartonPara(String tamanoCaja);

    /**
     * Lo que pesan los separadores de cartón que van dentro de cada caja, sea
     * del tamaño que sea (dos de 0,08 kg = 0,16 kg).
     *
     * Es configuración ({@code packing-list.separadores-carton}) y no una
     * constante del programa, por el mismo motivo que las taras: es un dato
     * del almacén y se corrige cuando se vuelve a pesar. Sin configurar vale
     * 0, que es lo que ven los tests unitarios y lo que hacía el programa
     * antes de contarlos.
     */
    double pesoSeparadoresKg();

    /**
     * Tara completa de una caja de ese tamaño: el cartón MÁS los separadores
     * que lleva dentro. Es la que se suma o se resta para pasar de peso neto a
     * bruto y al revés en todos los caminos del programa: la inferencia de la
     * revisión, la memoria de pesos del taller y el escalado de las cajas que
     * van a medias.
     *
     * La suma vive aquí y no en cada servicio que convierte pesos: contados en
     * unos sitios y no en otros, la misma caja saldría con un bruto distinto
     * según se hubiera llegado desde la revisión o desde el packing del
     * taller, y ese descuadre no lo ve nadie hasta comparar dos documentos.
     *
     * No se redondea aquí: redondea quien escribe el peso, a los dos decimales
     * que admiten la pantalla y el packing list.
     */
    default Optional<Double> taraPara(String tamanoCaja) {
        return taraCartonPara(tamanoCaja).map(carton -> carton + pesoSeparadoresKg());
    }

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

    /** Volumen en cm³ de un "LxAnchoxAlto", o 0 si no se puede leer así. */
    private static long volumen(String tamano) {
        return MedidaCaja.parse(tamano).map(MedidaCaja::volumen).orElse(0L);
    }
}

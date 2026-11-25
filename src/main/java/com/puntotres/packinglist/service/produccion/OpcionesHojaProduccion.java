package com.puntotres.packinglist.service.produccion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.puntotres.packinglist.model.DatosHojaProduccion.LineaProduccion;

/**
 * Las listas de los desplegables del formulario de Hojas de Producción. Son
 * constantes del programa y no datos del almacén (al revés que las taras):
 * las dio el usuario con la feature y cambian con el código.
 *
 * Todas las opciones «sugieren» un texto: el desplegable escribe en el campo
 * de texto de al lado y la persona puede corregirlo a mano. Por eso aquí solo
 * hay listas de textos, sin claves.
 */
public final class OpcionesHojaProduccion {

    /** El «no lleva» de todos los desplegables. */
    public static final String NADA = LineaProduccion.NADA;

    public static final List<String> MARCADO_INTERIOR = List.of("MADE IN MOROCCO", "MADE IN SPAIN", NADA);

    /** Acabado del marcado exterior y de la fornitura. */
    public static final List<String> COLORES_METAL = List.of("ORO", "NIQUEL", "ORO VIEJO", NADA);

    public static final List<String> FORNITURA = COLORES_METAL;

    public static final List<String> MARCAS_HILO = List.of("Gütermann", "Serafil");

    /** Los talleres que nombró el usuario; a ellos se suman los ya usados en hojas guardadas. */
    public static final List<String> TALLERES_CONOCIDOS = List.of("SWEET BAG", "C&C LEATHER", "ANTONIO MUÑIZ", "ADRIAN");

    /**
     * El marcado interior con el que arranca cada línea según el taller
     * (decisión del usuario): los de España marcan MADE IN SPAIN y los de
     * Marruecos MADE IN MOROCCO. Solo propone: se puede cambiar a mano. La
     * clave es el taller en mayúsculas; la pantalla compara además sin acentos
     * («ANTONIO MUNIZ» también vale).
     */
    public static final Map<String, String> MARCADO_POR_TALLER = Map.of(
            "ADRIAN", "MADE IN SPAIN",
            "ANTONIO MUÑIZ", "MADE IN SPAIN",
            "C&C LEATHER", "MADE IN MOROCCO",
            "SWEET BAG", "MADE IN MOROCCO");

    private static final List<String> TAMANOS_AMI = List.of("4CM", "4.5CM", "5CM");
    private static final List<String> TAMANOS_APC = List.of("2.5CM", "3CM", "3.5CM", "4CM");
    private static final List<String> TAMANOS_TODOS = List.of("2.5CM", "3CM", "3.5CM", "4CM", "4.5CM", "5CM");

    private OpcionesHojaProduccion() {
    }

    /**
     * Los tamaños del marcado exterior que se ofrecen para un cliente. AMI: 4,
     * 4.5 y 5 cm; APC: 2.5, 3, 3.5 y 4 cm; los demás, todos. Siempre con
     * {@link #NADA} al final: APC va por defecto sin tamaño (decisión del
     * usuario) y en los demás también se puede quitar.
     */
    public static List<String> tamanosMarcado(String cliente) {
        String clave = cliente == null ? "" : cliente.trim().toUpperCase(Locale.ROOT);
        List<String> base = switch (clave) {
            case "AMI" -> TAMANOS_AMI;
            case "APC" -> TAMANOS_APC;
            default -> TAMANOS_TODOS;
        };
        List<String> tamanos = new ArrayList<>(base);
        tamanos.add(NADA);
        return List.copyOf(tamanos);
    }

    /**
     * El tamaño con el que arranca el marcado exterior de un cliente: AMI
     * empieza en 4 cm (el más habitual de sus tres); APC y los demás sin
     * tamaño, que es como lo pidió el usuario para APC.
     */
    public static String tamanoPorDefecto(String cliente) {
        String clave = cliente == null ? "" : cliente.trim().toUpperCase(Locale.ROOT);
        return "AMI".equals(clave) ? "4CM" : "";
    }

    /**
     * El texto del marcado exterior a partir de sus tres partes: se unen con
     * un espacio, saltando las vacías y los «-»; sin ninguna parte, «-». La regla
     * vive en {@link LineaProduccion#marcadoExterior(String, String, String)}, que es
     * quien la aplica al imprimir; aquí solo se ofrece junto a las listas.
     */
    public static String marcadoExterior(String nombre, String color, String tamano) {
        return LineaProduccion.marcadoExterior(nombre, color, tamano);
    }
}

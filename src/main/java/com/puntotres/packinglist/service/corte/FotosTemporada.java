package com.puntotres.packinglist.service.corte;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Las fotos de la temporada por modelo, cada lista ordenada por nombre de
 * fichero, y los avisos de lo que había en el zip y no se usa.
 */
public record FotosTemporada(Map<String, List<FotoModelo>> porModelo, List<String> avisos) {

    public FotosTemporada {
        Map<String, List<FotoModelo>> copia = new TreeMap<>();
        porModelo.forEach((modelo, fotos) -> copia.put(modelo, List.copyOf(fotos)));
        porModelo = Collections.unmodifiableMap(copia);
        avisos = List.copyOf(avisos);
    }

    /**
     * Ninguna foto: la carpeta de fotos es opcional y, sin ella, salen las
     * órdenes de corte con el hueco de la foto en blanco y ningún Word de fotos.
     */
    public static FotosTemporada vacia() {
        return new FotosTemporada(Map.of(), List.of());
    }

    /** Las fotos de un modelo, o ninguna. La foto es del modelo: vale para todas sus pieles. */
    public List<FotoModelo> de(String modelo) {
        return porModelo.getOrDefault(ReferenciaCorte.normalizar(modelo), List.of());
    }

    /**
     * La foto que la orden de corte lleva si nadie elige otra: la que se llame
     * {@code principal}, si no {@code main}, si no {@code 0} —con cualquier
     * extensión y sin mirar mayúsculas—, y si no la primera por nombre, que es
     * la de siempre. Índice en {@link #de(String)}.
     */
    public int indicePorDefecto(String modelo) {
        List<FotoModelo> fotos = de(modelo);
        for (String preferido : NOMBRES_PRINCIPAL) {
            for (int i = 0; i < fotos.size(); i++) {
                if (sinExtension(fotos.get(i).nombreOriginal()).equalsIgnoreCase(preferido)) {
                    return i;
                }
            }
        }
        return 0;
    }

    /** De más a menos prioridad. */
    private static final List<String> NOMBRES_PRINCIPAL = List.of("principal", "main", "0");

    private static String sinExtension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return (punto < 0 ? nombre : nombre.substring(0, punto)).trim();
    }

    public List<FotoModelo> todas() {
        return porModelo.values().stream().flatMap(List::stream).toList();
    }

    public int total() {
        return porModelo.values().stream().mapToInt(List::size).sum();
    }
}

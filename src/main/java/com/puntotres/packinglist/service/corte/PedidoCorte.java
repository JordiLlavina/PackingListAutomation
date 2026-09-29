package com.puntotres.packinglist.service.corte;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * El pedido de la temporada visto desde el corte: una entrada por referencia
 * (modelo + piel) con los bolsos de cada color.
 *
 * Los bolsos de un color son la suma de todas las líneas con esa referencia y
 * ese color, sin mirar destinación, talla ni número de pedido: al cortador le
 * da igual a dónde vaya el bolso, lo que corta es la piel. Salen TODAS las
 * referencias, cinturones incluidos: el alcance de las órdenes es el pedido.
 */
public record PedidoCorte(List<ArticuloCorte> articulos, List<String> avisos) {

    public PedidoCorte {
        articulos = List.copyOf(articulos);
        avisos = List.copyOf(avisos);
    }

    public static PedidoCorte agrupar(List<LineaCorte> lineas,
                                      Function<String, ReferenciaCorte> partir) {
        Map<String, Map<String, Integer>> porReferencia = new LinkedHashMap<>();
        Map<String, String> nombres = new LinkedHashMap<>();
        for (LineaCorte linea : lineas) {
            String referencia = ReferenciaCorte.normalizar(linea.referencia());
            if (referencia.isEmpty()) {
                continue;
            }
            if (!linea.nombreModelo().isEmpty()) {
                nombres.putIfAbsent(referencia, linea.nombreModelo());
            }
            String color = linea.color() == null ? "" : linea.color().trim();
            porReferencia.computeIfAbsent(referencia, clave -> new LinkedHashMap<>())
                    .merge(color, Math.max(0, linea.cantidad()), Integer::sum);
        }

        List<ArticuloCorte> articulos = new ArrayList<>();
        List<String> sinPiel = new ArrayList<>();
        porReferencia.forEach((referencia, colores) -> {
            ReferenciaCorte partida = partir.apply(referencia);
            if (!partida.tienePiel()) {
                sinPiel.add(referencia);
            }
            articulos.add(new ArticuloCorte(partida, colores.entrySet().stream()
                    .map(color -> new ColorCorte(color.getKey(), color.getValue()))
                    .toList(), nombres.getOrDefault(referencia, "")));
        });
        articulos.sort(Comparator
                .comparing((ArticuloCorte articulo) -> articulo.referencia().modelo())
                .thenComparing(articulo -> articulo.referencia().piel()));

        List<String> avisos = new ArrayList<>();
        if (!sinPiel.isEmpty()) {
            avisos.add("Referencias del pedido sin la piel separada, que salen con el modelo "
                    + "entero y la piel en blanco: " + String.join(", ", sinPiel));
        }
        boolean sinCantidades = !articulos.isEmpty() && articulos.stream()
                .flatMap(articulo -> articulo.colores().stream())
                .allMatch(color -> color.bolsos() == 0);
        if (sinCantidades) {
            avisos.add("El excel de pedido no trae cantidades: el número de bolsos de cada "
                    + "color hay que teclearlo");
        }
        return new PedidoCorte(articulos, avisos);
    }

    /** Los modelos del pedido, sin repetir y en orden: los nombres de carpeta que se buscan en el zip. */
    public Set<String> modelos() {
        Set<String> modelos = new LinkedHashSet<>();
        articulos.forEach(articulo -> modelos.add(articulo.referencia().modelo()));
        return modelos;
    }
}

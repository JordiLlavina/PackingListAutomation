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
 *
 * El color se suma por su CÓDIGO, que es lo que lo identifica; la descripción
 * es la primera que traiga el pedido para ese código, si trae alguna.
 */
public record PedidoCorte(List<ArticuloCorte> articulos, List<String> avisos) {

    public PedidoCorte {
        articulos = List.copyOf(articulos);
        avisos = List.copyOf(avisos);
    }

    public static PedidoCorte agrupar(List<LineaCorte> lineas,
                                      Function<String, ReferenciaCorte> partir) {
        return agrupar(lineas, partir, true);
    }

    /**
     * {@code avisarSinPiel} = false para el excel de producción: ahí el
     * nombre de la piel ya viene escrito, y la referencia de piel solo sirve
     * para la memoria, que ese camino no usa. Avisar de cada referencia de un
     * cliente genérico, que nunca la separa, enterraría lo que sí hay que leer.
     */
    public static PedidoCorte agrupar(List<LineaCorte> lineas,
                                      Function<String, ReferenciaCorte> partir,
                                      boolean avisarSinPiel) {
        Map<String, Map<String, ColorCorte>> porReferencia = new LinkedHashMap<>();
        Map<String, String> nombres = new LinkedHashMap<>();
        for (LineaCorte linea : lineas) {
            String referencia = ReferenciaCorte.normalizar(linea.referencia());
            if (referencia.isEmpty()) {
                continue;
            }
            if (!linea.nombreModelo().isEmpty()) {
                nombres.putIfAbsent(referencia, linea.nombreModelo());
            }
            // Sin código (un pedido raro), el color es su descripción. La
            // prensa del mismo color se suma aparte: es otra orden de corte.
            String clave = (linea.prensa() ? "PRENSA " : "")
                    + (linea.codigoColor().isEmpty() ? linea.descripcionColor() : linea.codigoColor());
            porReferencia.computeIfAbsent(referencia, r -> new LinkedHashMap<>())
                    .merge(clave, new ColorCorte(linea.codigoColor(), linea.descripcionColor(),
                                    Math.max(0, linea.cantidad()), linea.prensa()),
                            (anterior, nuevo) -> new ColorCorte(anterior.codigo(),
                                    anterior.descripcion().isEmpty() ? nuevo.descripcion()
                                            : anterior.descripcion(),
                                    anterior.bolsos() + nuevo.bolsos(), anterior.prensa()));
        }

        List<ArticuloCorte> articulos = new ArrayList<>();
        List<String> sinPiel = new ArrayList<>();
        porReferencia.forEach((referencia, colores) -> {
            ReferenciaCorte partida = partir.apply(referencia);
            if (!partida.tienePiel()) {
                sinPiel.add(referencia);
            }
            articulos.add(new ArticuloCorte(partida, List.copyOf(colores.values()),
                    nombres.getOrDefault(referencia, "")));
        });
        articulos.sort(Comparator
                .comparing((ArticuloCorte articulo) -> articulo.referencia().modelo())
                .thenComparing(articulo -> articulo.referencia().piel()));

        List<String> avisos = new ArrayList<>();
        if (avisarSinPiel && !sinPiel.isEmpty()) {
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

    /**
     * Los códigos de color a los que el pedido no les da descripción, sin
     * repetir y en orden: los que hay que buscar en la Tabla colores Clientes.
     * En AMI, que trae el Libellé coloris, no sale ninguno.
     */
    public Set<String> codigosSinDescripcion() {
        Set<String> codigos = new LinkedHashSet<>();
        articulos.forEach(articulo -> articulo.colores().stream()
                .filter(color -> color.descripcion().isEmpty() && !color.codigo().isEmpty())
                .forEach(color -> codigos.add(color.codigo())));
        return codigos;
    }

    /**
     * El mismo pedido con la descripción de cada color que no la traía,
     * sacada de {@code descripciones} (código → descripción). Lo que el
     * pedido sí trae manda: la tabla solo rellena huecos.
     */
    public PedidoCorte conDescripciones(Map<String, String> descripciones) {
        List<ArticuloCorte> completados = articulos.stream()
                .map(articulo -> new ArticuloCorte(articulo.referencia(), articulo.colores().stream()
                        .map(color -> color.descripcion().isEmpty()
                                ? color.conDescripcion(descripciones.getOrDefault(color.codigo(), ""))
                                : color)
                        .toList(), articulo.nombreModelo()))
                .toList();
        return new PedidoCorte(completados, avisos);
    }
}

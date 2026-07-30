package com.puntotres.packinglist.service.etiquetas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.CajaFisica;

/**
 * Traduce las líneas del packing list de una caja física a la lista de
 * ARTÍCULOS que hay dentro, que es la unidad con la que trabajan las
 * etiquetas: un artículo = una referencia identificable con sus propios
 * códigos de barras.
 *
 * Hasta ahora los generadores colapsaban la caja a su línea líder y el resto
 * de artículos desaparecía de la etiqueta. Esto lo pone en un solo sitio para
 * que lo compartan todos los clientes.
 *
 * El criterio de qué parte un artículo lo decide quien llama, porque no es
 * igual en todos los clientes:
 * <ul>
 * <li><b>bolsos</b> (agruparPorTalla = false): referencia + color.</li>
 * <li><b>cinturones</b> (agruparPorTalla = true): referencia + color + talla,
 * porque cada talla tiene su propio EAN-13 en el excel de pedido.</li>
 * </ul>
 *
 * Con una sola línea, o con varias líneas del mismo artículo, sale un único
 * artículo: el comportamiento anterior es el caso particular de N=1.
 */
public final class ArticulosDeCaja {

    private static final String SEPARADOR = " / ";

    private ArticulosDeCaja() {
    }

    /** Los artículos de la caja, en orden de primera aparición en el packing list. */
    public static List<ArticuloEtiqueta> de(CajaFisica caja, boolean agruparPorTalla) {
        Map<String, ArticuloEtiqueta> porClave = new LinkedHashMap<>();
        for (CajaData linea : caja.lineas()) {
            String talla = agruparPorTalla ? linea.getTalla() : null;
            String clave = linea.getReferencia() + "|" + linea.getCodigoColor() + "|" + talla;
            porClave.merge(clave,
                    new ArticuloEtiqueta(linea.getReferencia(), linea.getCodigoColor(),
                            talla, linea.getCantidad()),
                    (previo, nuevo) -> new ArticuloEtiqueta(previo.referencia(),
                            previo.codigoColor(), previo.talla(),
                            previo.cantidad() + nuevo.cantidad()));
        }
        return List.copyOf(porClave.values());
    }

    /**
     * Un campo de todos los artículos concatenado con " / ", en el mismo
     * orden. Los valores repetidos NO se deduplican: la etiqueta se lee en
     * paralelo, un artículo por posición en cada campo.
     */
    public static String unir(List<ArticuloEtiqueta> articulos,
                              Function<ArticuloEtiqueta, String> campo) {
        return articulos.stream().map(campo).collect(Collectors.joining(SEPARADOR));
    }
}

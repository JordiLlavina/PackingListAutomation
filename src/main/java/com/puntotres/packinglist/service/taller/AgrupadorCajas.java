package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.TipoMezcla;

/**
 * Reparte los artículos de un envío en cajas físicas.
 *
 * Tres reglas gobiernan esto, y ninguna es evidente leyendo el código:
 *
 * <ol>
 * <li><b>Mezclar exige el mismo cartón.</b> Una caja no es una capacidad
 * abstracta: es un cartón de una medida concreta. Dos referencias que se
 * empaquetan en cartones distintos no pueden compartir bulto por mucho que la
 * norma comercial del cliente permita mezclar modelos.</li>
 *
 * <li><b>Se mezcla solo si ahorra una caja.</b> Un bulto mixto complica la
 * etiqueta y el packing list, así que se comparan los dos recuentos —cajas
 * puras contra cajas mezcladas— y solo gana la mezcla cuando de verdad quita
 * un bulto de encima. En caso de empate, cajas puras.</li>
 *
 * <li><b>El reparto es equitativo.</b> 31 unidades con 10 por caja salen
 * 8+8+8+7, nunca 10+10+10+1: una caja casi vacía se aplasta con el peso de
 * las de encima.</li>
 * </ol>
 *
 * En una caja mixta cada unidad ocupa {@code 1/unidadesPorCaja} de la caja.
 * Es la única forma de dar sentido a "diez por caja" cuando dentro hay dos
 * artículos que no ocupan lo mismo; contar unidades sueltas metería doce
 * bolsos grandes donde caben diez.
 *
 * El orden importa: los artículos se recorren por referencia y, dentro de
 * cada una, por color, de modo que se agotan los colores de una referencia
 * antes de empezar otra. Eso es lo que pide el almacén, y además hace el
 * resultado reproducible.
 */
@Service
public class AgrupadorCajas {

    /**
     * @param articulos ya repartidos por destinación, en el orden en que se
     *                  quieren empaquetar
     * @param mezcla    la norma del cliente para esa destinación
     */
    public List<CajaGenerada> agrupar(List<ArticuloDestinado> articulos, TipoMezcla mezcla) {
        List<CajaGenerada> cajas = new ArrayList<>();
        for (Map.Entry<String, List<ArticuloDestinado>> grupo : porGrupos(articulos, mezcla).entrySet()) {
            cajas.addAll(cajasDe(grupo.getValue()));
        }
        return cajas;
    }

    /**
     * Los artículos que PODRÍAN compartir caja, agrupados. Que de verdad la
     * compartan lo decide después la regla del ahorro.
     */
    private static Map<String, List<ArticuloDestinado>> porGrupos(
            List<ArticuloDestinado> articulos, TipoMezcla mezcla) {
        Map<String, List<ArticuloDestinado>> grupos = new LinkedHashMap<>();
        for (ArticuloDestinado articulo : articulos) {
            grupos.computeIfAbsent(claveDeGrupo(articulo, mezcla), clave -> new ArrayList<>())
                    .add(articulo);
        }
        return grupos;
    }

    private static String claveDeGrupo(ArticuloDestinado articulo, TipoMezcla mezcla) {
        // La destinación y el cartón separan siempre: un palet no mezcla
        // destinaciones y una caja no puede ser dos cartones a la vez.
        String base = articulo.destino() + "|" + articulo.medidaCaja();
        return switch (mezcla) {
            case NINGUNA -> base + "|" + articulo.referencia() + "|" + articulo.color()
                    + "|" + articulo.talla();
            case MISMO_PEDIDO -> base + "|" + articulo.pedido();
            case LIBRE -> base;
        };
    }

    /**
     * Las cajas de un grupo que puede mezclar. Se comparan los dos recuentos y
     * gana el que use menos cartones; en empate, cajas puras.
     */
    private static List<CajaGenerada> cajasDe(List<ArticuloDestinado> grupo) {
        int puras = grupo.stream()
                .mapToInt(a -> cajasNecesarias(a.cantidad(), a.unidadesPorCaja()))
                .sum();
        int mezcladas = (int) Math.ceil(ocupacionTotal(grupo) - 1e-9);

        if (mezcladas >= puras) {
            List<CajaGenerada> cajas = new ArrayList<>();
            for (ArticuloDestinado articulo : grupo) {
                cajas.addAll(cajasDeUnArticulo(articulo));
            }
            return cajas;
        }
        return cajasMezcladas(grupo, mezcladas);
    }

    /** Un artículo solo, repartido equitativamente entre sus cajas. */
    private static List<CajaGenerada> cajasDeUnArticulo(ArticuloDestinado articulo) {
        int nCajas = cajasNecesarias(articulo.cantidad(), articulo.unidadesPorCaja());
        int base = articulo.cantidad() / nCajas;
        int resto = articulo.cantidad() % nCajas;

        List<CajaGenerada> cajas = new ArrayList<>();
        for (int i = 0; i < nCajas; i++) {
            int unidades = base + (i < resto ? 1 : 0);
            cajas.add(new CajaGenerada(articulo.destino(), articulo.medidaCaja(),
                    List.of(contenido(articulo, unidades))));
        }
        return cajas;
    }

    /**
     * Un grupo entero repartido en {@code nCajas} bultos. Se recorren los
     * artículos en orden llenando la caja actual hasta el llenado objetivo, así
     * que los artículos salen contiguos y solo se mezclan en las fronteras: la
     * mayoría de las cajas siguen siendo puras aunque el grupo se mezcle.
     */
    private static List<CajaGenerada> cajasMezcladas(List<ArticuloDestinado> grupo, int nCajas) {
        double objetivoPorCaja = ocupacionTotal(grupo) / nCajas;
        ArticuloDestinado primero = grupo.get(0);

        List<List<ContenidoCaja>> cajas = new ArrayList<>();
        List<ContenidoCaja> actual = new ArrayList<>();
        double ocupacionActual = 0;

        for (ArticuloDestinado articulo : grupo) {
            int pendientes = articulo.cantidad();
            double porUnidad = 1.0 / articulo.unidadesPorCaja();
            while (pendientes > 0) {
                // Cuántas unidades caben en la caja actual sin pasarse de
                // llena; y de esas, cuántas tocan para no adelantar trabajo
                // que corresponde a las cajas siguientes.
                int hastaLlenar = (int) Math.floor((1 - ocupacionActual) / porUnidad + 1e-9);
                int hastaObjetivo = cajas.size() == nCajas - 1
                        ? pendientes
                        : (int) Math.ceil((objetivoPorCaja - ocupacionActual) / porUnidad - 1e-9);
                int caben = Math.min(pendientes, Math.min(hastaLlenar, Math.max(hastaObjetivo, 0)));

                if (caben <= 0) {
                    cajas.add(actual);
                    actual = new ArrayList<>();
                    ocupacionActual = 0;
                    continue;
                }
                actual.add(contenido(articulo, caben));
                ocupacionActual += caben * porUnidad;
                pendientes -= caben;
            }
        }
        if (!actual.isEmpty()) {
            cajas.add(actual);
        }
        return cajas.stream()
                .map(contenido -> new CajaGenerada(
                        primero.destino(), primero.medidaCaja(), contenido))
                .toList();
    }

    private static ContenidoCaja contenido(ArticuloDestinado articulo, int unidades) {
        return new ContenidoCaja(articulo.referencia(), articulo.color(), articulo.talla(),
                articulo.pedido(), unidades);
    }

    private static double ocupacionTotal(List<ArticuloDestinado> grupo) {
        return grupo.stream()
                .mapToDouble(a -> a.cantidad() / (double) a.unidadesPorCaja())
                .sum();
    }

    private static int cajasNecesarias(int cantidad, int unidadesPorCaja) {
        return Math.max(1, (cantidad + unidadesPorCaja - 1) / unidadesPorCaja);
    }
}

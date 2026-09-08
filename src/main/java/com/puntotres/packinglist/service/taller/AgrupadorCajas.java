package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.TipoMezcla;
import com.puntotres.packinglist.model.CajaData;

/**
 * Reparte los artículos de un envío en cajas físicas.
 *
 * Cinco reglas gobiernan esto, y ninguna es evidente leyendo el código:
 *
 * <ol>
 * <li><b>Mezclar exige el mismo cartón.</b> Una caja no es una capacidad
 * abstracta: es un cartón de una medida concreta. Dos referencias que se
 * empaquetan en cartones distintos no pueden compartir bulto por mucho que la
 * norma comercial del cliente permita mezclar modelos.</li>
 *
 * <li><b>Un bolso y un cinturón nunca comparten caja.</b> No es una norma
 * comercial ni una cuestión de hueco: son artículos de naturaleza distinta y
 * el almacén los prepara por separado. Manda sobre cualquier
 * {@link TipoMezcla}, incluso {@code LIBRE}.</li>
 *
 * <li><b>Las tallas de un mismo color sí comparten caja</b>, y deben hacerlo
 * cuando eso ahorra un bulto. Cada talla es un artículo distinto —tiene su
 * propio código de barras— pero separarlas dejaría media caja vacía por
 * talla, y una referencia de cinturón se sirve en cinco o seis a la vez.</li>
 *
 * <li><b>Se mezcla solo si ahorra una caja.</b> Un bulto mixto complica la
 * etiqueta y el packing list, así que se comparan los dos recuentos —cajas
 * puras contra cajas mezcladas— y solo gana la mezcla cuando de verdad quita
 * un bulto de encima. En caso de empate, cajas puras.</li>
 *
 * <li><b>El reparto es equitativo.</b> 31 unidades con 10 por caja salen
 * 8+8+8+7, nunca 10+10+10+1: una caja casi vacía se aplasta con el peso de
 * las de encima. Ojo: el reparto equitativo NO añade cajas. Primero se fija
 * el número mínimo de cajas y solo después se reparte dentro de ese número.</li>
 * </ol>
 *
 * En una caja mixta cada unidad ocupa {@code 1/unidadesPorCaja} de la caja.
 * Es la única forma de dar sentido a "diez por caja" cuando dentro hay dos
 * artículos que no ocupan lo mismo; contar unidades sueltas metería doce
 * bolsos grandes donde caben diez.
 *
 * Dentro de un grupo que se mezcla, los artículos se empaquetan empezando por
 * la unidad más voluminosa (ver {@code porUnidadMasVoluminosa}). Los que
 * ocupan lo mismo conservan el orden con el que llegan —referencia, y dentro
 * de ella color—, así que se siguen agotando los colores de una referencia
 * antes de empezar otra y el resultado es reproducible.
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
        // Tres cosas separan SIEMPRE, gobierne lo que gobierne la norma del
        // cliente: la destinación (un palet no mezcla destinaciones), el
        // cartón (una caja no puede ser dos cartones a la vez) y el tipo de
        // artículo (un bolso y un cinturón no viajan en el mismo bulto).
        String base = articulo.destino() + "|" + articulo.medidaCaja()
                + "|" + tipoDeArticulo(articulo);
        return switch (mezcla) {
            // La TALLA no entra en la clave: las tallas de un mismo color son
            // artículos distintos —cada una con su código de barras— pero
            // comparten bulto de buena gana, y separarlas dejaría media caja
            // vacía por talla. Es lo normal en cinturones, donde una
            // referencia se sirve en cinco o seis tallas a la vez.
            case NINGUNA -> base + "|" + articulo.referencia() + "|" + articulo.color();
            case MISMO_PEDIDO -> base + "|" + articulo.pedido();
            case LIBRE -> base;
        };
    }

    /**
     * Bolso o cinturón. Se reconoce por el prefijo de la referencia, que es la
     * misma regla con la que {@code AmiGenerador} elige entre la plantilla de
     * bolsos y la de matriz de tallas: si aquí se decidiera de otra forma, un
     * bulto podría acabar partido entre dos packing lists.
     */
    private static String tipoDeArticulo(ArticuloDestinado articulo) {
        return CajaData.esCinturon(articulo.referencia()) ? "CINTURON" : "BOLSO";
    }

    /**
     * Las cajas de un grupo que puede mezclar. Se comparan los dos recuentos y
     * gana el que use menos cartones; en empate, cajas puras.
     *
     * El recuento de la mezcla es una COTA: dice cuántas cajas harían falta si
     * el contenido se pudiera trocear a voluntad, y el empaquetado real no
     * siempre la alcanza cuando los artículos del grupo no ocupan lo mismo
     * (ocho unidades de a 1/10 no rellenan el hueco que deja una de 1/3). Por
     * eso se cuenta lo que de verdad ha salido y, si no ha ahorrado nada, se
     * vuelve a las cajas puras: mezclar sin ahorrar un bulto es lo peor de los
     * dos mundos —mismo número de cajas y encima mixtas— y la regla del empate
     * quedaría incumplida sin que nadie lo viera.
     */
    private static List<CajaGenerada> cajasDe(List<ArticuloDestinado> grupo) {
        int puras = grupo.stream()
                .mapToInt(a -> cajasNecesarias(a.cantidad(), a.unidadesPorCaja()))
                .sum();
        int cota = (int) Math.ceil(ocupacionTotal(grupo) - 1e-9);
        if (cota >= puras) {
            return cajasPuras(grupo);
        }

        List<ArticuloDestinado> ordenado = porUnidadMasVoluminosa(grupo);
        List<CajaGenerada> mezcladas = cajasMezcladas(ordenado, cota);
        if (mezcladas.size() > cota) {
            // No se ha alcanzado la cota: el llenado objetivo se calculó para
            // menos cajas de las que han salido, así que las primeras van
            // llenas y el sobrante se queda en una última caja casi vacía. Se
            // reparte otra vez sobre las cajas que de verdad hacen falta, que
            // es la misma regla de equidad de un artículo solo: mismo número
            // de bultos, pero ninguno al diez por ciento.
            List<CajaGenerada> equilibradas = cajasMezcladas(ordenado, mezcladas.size());
            if (equilibradas.size() <= mezcladas.size()) {
                mezcladas = equilibradas;
            }
        }
        return mezcladas.size() < puras ? mezcladas : cajasPuras(grupo);
    }

    /** Cada artículo del grupo en sus propias cajas, sin mezclar nada. */
    private static List<CajaGenerada> cajasPuras(List<ArticuloDestinado> grupo) {
        List<CajaGenerada> cajas = new ArrayList<>();
        for (ArticuloDestinado articulo : grupo) {
            cajas.addAll(cajasDeUnArticulo(articulo));
        }
        return cajas;
    }

    /**
     * El grupo ordenado por unidad más voluminosa primero (la que menos
     * unidades mete en una caja).
     *
     * Es la regla clásica de empaquetado: colocar primero lo que peor encaja y
     * dejar lo menudo para rellenar huecos. Al revés, las unidades pequeñas
     * llenan las cajas y la grande ya no cabe en ninguna, y hace falta un
     * cartón más para nada.
     *
     * El orden es ESTABLE, así que los artículos que ocupan lo mismo conservan
     * el que traían —referencia, y dentro de ella color—, que es como el
     * almacén espera ver el packing.
     */
    private static List<ArticuloDestinado> porUnidadMasVoluminosa(List<ArticuloDestinado> grupo) {
        return grupo.stream()
                .sorted(Comparator.comparingDouble(
                        (ArticuloDestinado a) -> 1.0 / a.unidadesPorCaja()).reversed())
                .toList();
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

package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.puntotres.packinglist.service.ApcPedidoExcel;
import com.puntotres.packinglist.service.etiquetas.AmiPedidoExcel;
import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres.Aviso;
import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres.Columna;
import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres.Fila;

/**
 * Lo que se decide en Java después de que Claude haya convertido el excel
 * del taller: todo lo que es una consulta a una tabla y no una lectura.
 *
 * Claude lee y separa (qué celda es el color, dónde acaba el modelo); pero
 * «¿qué pedido es F63023 en LAW para Douanes USA?» es un cruce contra el
 * excel de pedido que tiene una sola respuesta correcta, y hacerlo aquí
 * tiene tres ventajas sobre pedírselo al modelo: es el mismo criterio que
 * {@code PedidoCompletionService} (solo cuando la fila es única, nunca
 * adivinando), es auditable fila a fila, y no gasta razonamiento. Por eso el
 * prompt le prohíbe al modelo resolver el código de pedido.
 *
 * Cada cambio deja un {@link Aviso} con su fila. Lo unívoco (una referencia
 * completada por su código, un color traducido por la tabla, un pedido con
 * una sola fila posible) es <b>informativo</b>: queda como comentario en la
 * celda para saber de dónde salió, pero no pide revisión. Lo que no es
 * unívoco, o no cuadra, se queda como lo escribió el taller y sí pide
 * <b>revisar</b>: el excel lo pinta y la pantalla lo enseña a la vista.
 *
 * <p>APC, por fila:
 * <ol>
 * <li>El color: si es un nombre de la tabla de colores del cliente, pasa a
 *     su código. Un nombre que la tabla no conoce se deja y se pide, una
 *     sola vez por nombre, que se añada en Colores y Pieles de Cliente: sin
 *     él no hay forma de resolver el pedido.</li>
 * <li>Con código de pedido: se completa la referencia si el pedido tiene una
 *     sola fila con ese sufijo y ese código ({@link ApcPedidoExcel#filasPara}),
 *     y se comprueba que el color del pedido es el escrito.</li>
 * <li>Sin código: se busca el pedido por sufijo de referencia + color +
 *     destinación (comparando destinos PADRE, porque el taller abrevia y el
 *     pedido nombra a la hija). Uno solo → se estampa el código y la
 *     referencia; varios o ninguno → aviso y nada más.</li>
 * </ol>
 * AMI: la referencia escrita sin puntos ni ceros se casa con la del pedido
 * ({@link IndiceCatalogoConversion#formaCorta}) y se comprueba que el color
 * es uno de los suyos.
 *
 * Para los dos: los totales que declara la hoja contra la suma de las filas.
 */
public final class AjustadorConversion {

    /**
     * Las consecuencias, lo que queda a la vista en el resumen plegado de
     * la pantalla; el hecho largo de cada fila se pliega debajo.
     */
    static final String VARIOS_PEDIDOS = "varios pedidos posibles: el código va a mano";
    static final String SIN_PEDIDO = "sin ningún pedido que case: reposición o error";
    static final String COLOR_NO_CUADRA = "el color no es el del pedido de ese código";
    static final String SIN_ESA_LINEA = "el código no existe para esa referencia";
    static final String CODIGO_AMBIGUO = "el código casa con varios artículos";
    static final String REFERENCIA_NO_PEDIDA = "referencia que no está en el pedido";
    static final String COLOR_NO_ES_DE_LA_REFERENCIA = "color que no es de la referencia";

    private AjustadorConversion() {
    }

    public static PackingFormatoPuntotres ajustar(PackingFormatoPuntotres packing,
                                                  PedidoParaConversion pedido) {
        List<Aviso> avisos = new ArrayList<>();
        List<Fila> filas = packing.filas();
        if (pedido != null && pedido.apc().isPresent()) {
            filas = ajustarApc(filas, pedido, pedido.apc().get(), avisos);
        } else if (pedido != null && pedido.ami().isPresent()) {
            filas = ajustarAmi(filas, pedido.ami().get(), avisos);
        }
        avisos.addAll(contrastarTotales(packing.conFilas(filas)));
        return packing.conFilas(filas).conAvisosAnadidos(avisos);
    }

    // --- APC ---

    private static List<Fila> ajustarApc(List<Fila> filas, PedidoParaConversion pedido,
                                         ApcPedidoExcel excel, List<Aviso> avisos) {
        List<Fila> ajustadas = new ArrayList<>();
        Map<String, Set<Integer>> coloresDesconocidos = new LinkedHashMap<>();
        for (int i = 0; i < filas.size(); i++) {
            int numero = i + 1;
            Fila fila = filas.get(i);
            if (!"APC".equalsIgnoreCase(limpio(fila.cliente())) && !limpio(fila.cliente()).isEmpty()) {
                ajustadas.add(fila);
                continue;
            }
            fila = colorAcodigo(fila, numero, pedido, avisos, coloresDesconocidos);
            fila = limpio(fila.codigoCliente()).isEmpty()
                    ? resolverPedido(fila, numero, pedido, excel, avisos)
                    : comprobarPedido(fila, numero, excel, avisos);
            ajustadas.add(fila);
        }
        coloresDesconocidos.forEach((nombre, lineas) -> avisos.add(Aviso.general(
                "El color '" + nombre + "' (filas " + unir(lineas) + ") no está en la tabla de "
                        + "colores de APC: añádelo en Colores y Pieles de Cliente con su código "
                        + "y vuelve a generar, o esas filas se quedan sin código de pedido")));
        return ajustadas;
    }

    private static Fila colorAcodigo(Fila fila, int numero, PedidoParaConversion pedido,
                                     List<Aviso> avisos,
                                     Map<String, Set<Integer>> desconocidos) {
        String color = limpio(fila.color());
        if (color.isEmpty()) {
            return fila;
        }
        Optional<String> codigo = pedido.codigoDeColor(color);
        if (codigo.isEmpty()) {
            if (!pedido.coloresCliente().containsKey(color.toUpperCase(Locale.ROOT))) {
                desconocidos.computeIfAbsent(color.toUpperCase(Locale.ROOT),
                        c -> new LinkedHashSet<>()).add(numero);
            }
            return fila;
        }
        if (codigo.get().equalsIgnoreCase(color)) {
            return fila;
        }
        avisos.add(Aviso.informativo(numero, Columna.COLOR, "El color '" + color + "' que escribe el taller es "
                + "el código " + codigo.get() + " según la tabla de colores"));
        return fila.conColor(codigo.get());
    }

    /** Con código escrito: completar la referencia y comprobar el color. */
    private static Fila comprobarPedido(Fila fila, int numero, ApcPedidoExcel excel,
                                        List<Aviso> avisos) {
        String referencia = limpio(fila.referencia());
        String codigo = limpio(fila.codigoCliente());
        List<ApcPedidoExcel.FilaPedido> candidatas = excel.filasPara(referencia, codigo);
        if (candidatas.isEmpty()) {
            avisos.add(Aviso.deFila(numero, Columna.CODIGO_CLIENTE, SIN_ESA_LINEA, "En el pedido no hay ninguna línea de " + referencia
                    + " con el código " + codigo + ": o el código o la referencia están mal "
                    + "escritos en la hoja del taller"));
            return fila;
        }
        if (candidatas.size() > 1) {
            avisos.add(Aviso.deFila(numero, Columna.REFERENCIA, CODIGO_AMBIGUO, "El código " + codigo + " con la referencia "
                    + referencia + " casa con varios artículos del pedido ("
                    + String.join(", ", candidatas.stream().map(ApcPedidoExcel.FilaPedido::referencia)
                            .distinct().toList()) + "): se deja como lo escribe el taller"));
            return fila;
        }
        ApcPedidoExcel.FilaPedido unica = candidatas.get(0);
        Set<String> coloresDelPedido = excel.coloresDe(unica.pedido());
        String color = limpio(fila.color()).toUpperCase(Locale.ROOT);
        if (!color.isEmpty() && !coloresDelPedido.isEmpty() && !coloresDelPedido.contains(color)) {
            avisos.add(Aviso.deFila(numero, Columna.COLOR, COLOR_NO_CUADRA, "El pedido " + unica.pedido() + " (código " + codigo
                    + ") es en " + String.join("/", coloresDelPedido) + " y la hoja del taller "
                    + "dice " + color + ": o el color o el código están mal. Se deja lo escrito"));
        }
        if (!unica.referencia().equalsIgnoreCase(referencia)) {
            avisos.add(Aviso.informativo(numero, Columna.REFERENCIA, "Referencia completada a " + unica.referencia()
                    + " (el taller escribe " + referencia + ") por el código " + codigo));
            return fila.conReferencia(unica.referencia());
        }
        return fila;
    }

    /** Sin código: buscarlo por sufijo + color + destinación, solo si es único. */
    private static Fila resolverPedido(Fila fila, int numero, PedidoParaConversion pedido,
                                       ApcPedidoExcel excel, List<Aviso> avisos) {
        String referencia = limpio(fila.referencia()).toUpperCase(Locale.ROOT);
        String color = limpio(fila.color()).toUpperCase(Locale.ROOT);
        String destinacion = limpio(fila.destinacion());
        if (referencia.isEmpty() || destinacion.isEmpty()
                || !pedido.coloresCliente().containsKey(color)) {
            // Sin color en código no hay cruce posible; el aviso de la tabla
            // de colores ya lo dice una vez por nombre.
            return fila;
        }
        Optional<String> padre = pedido.destinoPadre(destinacion);
        Map<String, String> pedidosPorArticulo = new LinkedHashMap<>();
        for (ApcPedidoExcel.LineaCatalogo linea : excel.catalogo()) {
            String articulo = limpio(linea.referencia()).toUpperCase(Locale.ROOT);
            if (!articulo.endsWith(referencia)
                    || !color.equals(limpio(linea.color()).toUpperCase(Locale.ROOT))
                    || !mismoDestino(pedido, padre, destinacion, linea.destino())) {
                continue;
            }
            pedidosPorArticulo.putIfAbsent(linea.pedido().trim(), articulo);
        }
        if (pedidosPorArticulo.isEmpty()) {
            avisos.add(Aviso.deFila(numero, Columna.CODIGO_CLIENTE, SIN_PEDIDO, "En el pedido no hay ninguna línea de " + referencia
                    + " en " + color + " para " + destinacion + ": se queda sin código de "
                    + "pedido. Puede ser una reposición, o un color o destinación mal escritos"));
            return fila;
        }
        if (pedidosPorArticulo.size() > 1) {
            avisos.add(Aviso.deFila(numero, Columna.CODIGO_CLIENTE, VARIOS_PEDIDOS, "Para " + referencia + " en " + color + " a "
                    + destinacion + " el pedido tiene varios números ("
                    + String.join(", ", pedidosPorArticulo.keySet()) + "): hay que poner el "
                    + "código a mano"));
            return fila;
        }
        Map.Entry<String, String> unico = pedidosPorArticulo.entrySet().iterator().next();
        String codigo = IndiceCatalogoConversion.sufijo(unico.getKey());
        avisos.add(Aviso.informativo(numero, Columna.CODIGO_CLIENTE, "Código de pedido " + codigo + " (" + unico.getKey()
                + ") puesto por ser el único de " + unico.getValue() + " en " + color + " para "
                + destinacion + "; el taller no lo escribía"));
        Fila conCodigo = fila.conCodigoCliente(codigo);
        return unico.getValue().equalsIgnoreCase(referencia) ? conCodigo
                : conCodigo.conReferencia(unico.getValue());
    }

    /**
     * La destinación de la fila y la de una línea del pedido son el mismo
     * sitio: mismo destino padre del catálogo del cliente o, si alguna no se
     * resuelve, el mismo nombre.
     */
    private static boolean mismoDestino(PedidoParaConversion pedido, Optional<String> padreFila,
                                        String destinacionFila, String destinoPedido) {
        String delPedido = limpio(destinoPedido);
        Optional<String> padrePedido = pedido.destinoPadre(delPedido);
        if (padreFila.isPresent() && padrePedido.isPresent()) {
            return padreFila.get().equalsIgnoreCase(padrePedido.get());
        }
        return destinacionFila.equalsIgnoreCase(delPedido);
    }

    // --- AMI ---

    private static List<Fila> ajustarAmi(List<Fila> filas, AmiPedidoExcel excel,
                                         List<Aviso> avisos) {
        Map<String, Set<String>> referenciasPorFormaCorta = new LinkedHashMap<>();
        Map<String, Set<String>> coloresPorReferencia = new LinkedHashMap<>();
        for (AmiPedidoExcel.LineaCatalogo linea : excel.catalogo()) {
            String referencia = limpio(linea.referencia()).toUpperCase(Locale.ROOT);
            referenciasPorFormaCorta.computeIfAbsent(
                    IndiceCatalogoConversion.formaCorta(referencia), f -> new LinkedHashSet<>())
                    .add(referencia);
            Set<String> colores = coloresPorReferencia.computeIfAbsent(referencia,
                    r -> new LinkedHashSet<>());
            colores.add(limpio(linea.colorCode()).toUpperCase(Locale.ROOT));
            colores.add(limpio(linea.colorNombre()).toUpperCase(Locale.ROOT));
            colores.add((limpio(linea.colorCode()) + " " + limpio(linea.colorNombre())).trim()
                    .toUpperCase(Locale.ROOT));
        }

        List<Fila> ajustadas = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            int numero = i + 1;
            Fila fila = filas.get(i);
            String referencia = limpio(fila.referencia()).toUpperCase(Locale.ROOT);
            if (referencia.isEmpty()
                    || (!"AMI".equalsIgnoreCase(limpio(fila.cliente()))
                            && !limpio(fila.cliente()).isEmpty())) {
                ajustadas.add(fila);
                continue;
            }
            Set<String> candidatas = referenciasPorFormaCorta.getOrDefault(
                    IndiceCatalogoConversion.formaCorta(referencia), Set.of());
            if (candidatas.size() == 1) {
                String delPedido = candidatas.iterator().next();
                if (!delPedido.equals(referencia)) {
                    avisos.add(Aviso.informativo(numero, Columna.REFERENCIA, "Referencia escrita como " + referencia
                            + " por el taller: en el pedido es " + delPedido));
                    fila = fila.conReferencia(delPedido);
                    referencia = delPedido;
                }
            } else if (candidatas.isEmpty()) {
                avisos.add(Aviso.deFila(numero, Columna.REFERENCIA, REFERENCIA_NO_PEDIDA, "La referencia " + referencia
                        + " no está en el pedido de esta temporada: se deja como la escribe el taller"));
            }
            String color = limpio(fila.color()).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
            Set<String> colores = coloresPorReferencia.get(referencia);
            if (!color.isEmpty() && colores != null && !colores.contains(color)) {
                avisos.add(Aviso.deFila(numero, Columna.COLOR, COLOR_NO_ES_DE_LA_REFERENCIA, "El color '" + color + "' no es ninguno de los "
                        + "de " + referencia + " en el pedido (" + String.join(", ",
                                colores.stream().filter(c -> c.contains(" ")).toList())
                        + "): comprueba el color o escríbelo con su código"));
            }
            ajustadas.add(fila);
        }
        return ajustadas;
    }

    // --- Totales ---

    static List<Aviso> contrastarTotales(PackingFormatoPuntotres packing) {
        List<Aviso> avisos = new ArrayList<>();
        Integer unidades = packing.resumen().unidadesTotales();
        if (unidades != null && unidades != packing.unidadesConvertidas()) {
            avisos.add(Aviso.general("La hoja del taller declara " + unidades
                    + " unidades y las filas convertidas suman " + packing.unidadesConvertidas()
                    + ": falta o sobra alguna línea, o una cantidad está mal leída. "
                    + "Revisa el excel antes de digerirlo"));
        }
        Integer cajas = packing.resumen().cajasTotales();
        if (cajas != null && cajas != packing.cajasConvertidas()) {
            avisos.add(Aviso.general("La hoja del taller declara " + cajas
                    + " cajas y las filas convertidas suman " + packing.cajasConvertidas()
                    + ". El programa no usa la numeración del taller, pero un descuadre "
                    + "puede ser una línea perdida: compruébalo"));
        }
        return avisos;
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private static String unir(Set<Integer> numeros) {
        return String.join(", ", numeros.stream().map(String::valueOf).toList());
    }
}

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
 * Para todos: las cajas que el taller llena con DOS pedidos
 * ({@link #repartirCajasCompartidas}) y los totales que declara la hoja
 * contra la suma de las filas.
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
    static final String CAJA_SIN_REPARTIR = "caja con dos pedidos: la cantidad va a mano";
    static final String CANTIDAD_DEDUCIDA = "cantidad deducida del pedido: comprobarla";

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
        filas = repartirCajasCompartidas(filas,
                pedido == null ? Optional.empty() : pedido.apc(), avisos);
        avisos.addAll(contrastarTotales(packing.conFilas(filas)));
        return packing.conFilas(filas).conAvisosAnadidos(avisos);
    }

    // --- Cajas con dos pedidos ---

    /** Filas seguidas (posiciones desde 0, las dos incluidas) que son una línea partida. */
    record Tramo(int desde, int hasta) {
    }

    /** Lo que le falta a un pedido para completarse con lo que ya llevan las demás filas. */
    private record Resto(String codigo, int pedidas, int llevan) {
        int faltan() {
            return pedidas - llevan;
        }
    }

    /**
     * Las cajas que el taller llena con dos pedidos ("WHOLSEAL 930/948"). El
     * modelo las parte en una fila por pedido, iguales en todo y SIN cantidad,
     * porque la hoja no dice cuántas unidades son de cada uno; aquí se intenta
     * repartirlas con lo que el cliente pidió.
     *
     * Solo se deduce con dos pedidos de APC y cuando la cuenta tiene una única
     * salida: o lo que les falta a los dos suma justo la caja, o uno de los
     * dos se completa con menos de una caja y al otro le cabe el resto (que
     * es por lo que el taller las junta: el pico de un pedido no llena un
     * cartón). "Lo que falta" es lo pedido menos lo que llevan las demás filas
     * de ESTA hoja: el programa no sabe de entregas anteriores, así que la
     * deducción va en amarillo y con la cuenta escrita, para que alguien la
     * compruebe. Si no sale, la cantidad se queda en blanco, por decisión del
     * usuario: un reparto a medias inventado acabaría en el packing del cliente.
     */
    static List<Fila> repartirCajasCompartidas(List<Fila> filas, Optional<ApcPedidoExcel> excel,
                                               List<Aviso> avisos) {
        List<Fila> repartidas = new ArrayList<>(filas);
        for (Tramo tramo : cajasCompartidas(filas)) {
            Fila primera = filas.get(tramo.desde());
            int total = unidadesDe(primera);
            String cajas = (primera.numeroDeCajas() > 1 ? "Las cajas " + limpio(primera.cajas())
                    + " llevan " : "La caja " + limpio(primera.cajas()) + " lleva ") + total
                    + " unidades";
            Optional<int[]> reparto = Optional.empty();
            Resto[] restos = new Resto[2];
            if (excel.isPresent() && tramo.hasta() == tramo.desde() + 1) {
                restos[0] = resto(filas, tramo.desde(), excel.get());
                restos[1] = resto(filas, tramo.hasta(), excel.get());
                reparto = deducir(restos[0], restos[1], total);
            }
            if (reparto.isEmpty()) {
                List<String> partes = new ArrayList<>();
                for (int i = tramo.desde(); i <= tramo.hasta(); i++) {
                    String codigo = limpio(filas.get(i).codigoCliente());
                    partes.add(codigo.isEmpty() ? limpio(filas.get(i).destinacion()) : codigo);
                }
                for (int i = tramo.desde(); i <= tramo.hasta(); i++) {
                    avisos.add(Aviso.deFila(i + 1, Columna.CANTIDAD, CAJA_SIN_REPARTIR, cajas
                            + " de los pedidos " + String.join(" y ", partes) + " y el taller no "
                            + "dice cuántas de cada uno: la cantidad se teclea a mano"));
                }
                continue;
            }
            // reparto = {unidades de la primera fila, cuál de las dos se completa}
            int paraPrimera = reparto.get()[0];
            Resto completo = restos[reparto.get()[1]];
            Resto otro = restos[1 - reparto.get()[1]];
            String cuenta = "Cantidad deducida del pedido, el taller no la escribe. " + cajas
                    + " entre los pedidos " + restos[0].codigo() + " y " + restos[1].codigo()
                    + ": al " + completo.codigo() + " le faltan " + completo.faltan()
                    + " para las " + completo.pedidas() + " que pide (las demás filas de la hoja "
                    + "llevan " + completo.llevan() + "), así que las otras "
                    + (total - completo.faltan()) + " son del " + otro.codigo() + ". Compruébalo";
            repartidas.set(tramo.desde(), primera.conCantidad(paraPrimera));
            repartidas.set(tramo.hasta(), filas.get(tramo.hasta()).conCantidad(total - paraPrimera));
            avisos.add(Aviso.deFila(tramo.desde() + 1, Columna.CANTIDAD, CANTIDAD_DEDUCIDA, cuenta));
            avisos.add(Aviso.deFila(tramo.hasta() + 1, Columna.CANTIDAD, CANTIDAD_DEDUCIDA, cuenta));
        }
        return repartidas;
    }

    /**
     * Los tramos de filas seguidas que son UNA línea del taller partida por
     * pedido: las mismas cajas, las mismas unidades por caja y ninguna con
     * cantidad. No se confunde con una caja mixta (la misma caja en dos
     * líneas del taller), porque ahí cada línea trae su cantidad.
     */
    static List<Tramo> cajasCompartidas(List<Fila> filas) {
        List<Tramo> tramos = new ArrayList<>();
        int i = 0;
        while (i < filas.size()) {
            int j = i;
            while (j + 1 < filas.size() && mismaLineaPartida(filas.get(i), filas.get(j + 1))) {
                j++;
            }
            if (j > i) {
                tramos.add(new Tramo(i, j));
            }
            i = j + 1;
        }
        return tramos;
    }

    private static boolean mismaLineaPartida(Fila una, Fila otra) {
        return una.cantidad() == null && otra.cantidad() == null
                && una.unidadesPorCaja() != null
                && una.unidadesPorCaja().equals(otra.unidadesPorCaja())
                && !limpio(una.cajas()).isEmpty()
                && limpio(una.cajas()).equals(limpio(otra.cajas()));
    }

    /** Las unidades de la línea entera: unidades por caja por número de cajas. */
    private static int unidadesDe(Fila fila) {
        return fila.unidadesPorCaja() * Math.max(1, fila.numeroDeCajas());
    }

    /** Las unidades de las cajas compartidas que se han quedado sin repartir. */
    static int unidadesSinRepartir(List<Fila> filas) {
        return cajasCompartidas(filas).stream()
                .mapToInt(tramo -> unidadesDe(filas.get(tramo.desde()))).sum();
    }

    /**
     * Lo pedido en el Document d'achat de la fila, para SU artículo y SU
     * color, y lo que ya llevan de él las demás filas de la hoja. Null si no
     * se puede saber: sin código, con un código que casa con varios artículos
     * o con un color que no es el de ese pedido.
     */
    private static Resto resto(List<Fila> filas, int posicion, ApcPedidoExcel excel) {
        Fila fila = filas.get(posicion);
        String codigo = limpio(fila.codigoCliente());
        String referencia = limpio(fila.referencia());
        String color = limpio(fila.color());
        if (codigo.isEmpty() || referencia.isEmpty() || color.isEmpty()) {
            return null;
        }
        List<ApcPedidoExcel.FilaPedido> candidatas = excel.filasPara(referencia, codigo);
        if (candidatas.size() != 1) {
            return null;
        }
        ApcPedidoExcel.FilaPedido unica = candidatas.get(0);
        Optional<Integer> pedidas = excel.comandasDe(unica.referencia(), color).stream()
                .filter(comanda -> comanda.pedido().equals(unica.pedido()))
                .map(ApcPedidoExcel.Comanda::cantidad).findFirst();
        if (pedidas.isEmpty()) {
            return null;
        }
        int llevan = 0;
        for (int i = 0; i < filas.size(); i++) {
            Fila otra = filas.get(i);
            if (i != posicion && otra.cantidad() != null
                    && codigo.equalsIgnoreCase(limpio(otra.codigoCliente()))
                    && referencia.equalsIgnoreCase(limpio(otra.referencia()))
                    && color.equalsIgnoreCase(limpio(otra.color()))) {
                llevan += otra.cantidad();
            }
        }
        return new Resto(codigo, pedidas.get(), llevan);
    }

    /**
     * El reparto de una caja entre dos pedidos, si la cuenta tiene una sola
     * salida: {unidades del primero, cuál de los dos se completa (0 o 1)}.
     */
    private static Optional<int[]> deducir(Resto primero, Resto segundo, int total) {
        if (primero == null || segundo == null) {
            return Optional.empty();
        }
        int a = primero.faltan();
        int b = segundo.faltan();
        if (a > 0 && b > 0 && a + b == total) {
            return Optional.of(new int[] {a, 0});
        }
        boolean seCompletaElPrimero = a > 0 && a < total && b >= total - a;
        boolean seCompletaElSegundo = b > 0 && b < total && a >= total - b;
        if (seCompletaElPrimero == seCompletaElSegundo) {
            // Ninguno, o los dos con repartos distintos: no hay una sola salida.
            return Optional.empty();
        }
        return Optional.of(seCompletaElPrimero ? new int[] {a, 0} : new int[] {total - b, 1});
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
        // Una caja con dos pedidos sin repartir va sin cantidad en sus filas,
        // pero sus unidades están en la hoja: no es una línea perdida.
        int convertidas = packing.unidadesConvertidas() + unidadesSinRepartir(packing.filas());
        if (unidades != null && unidades != convertidas) {
            avisos.add(Aviso.general("La hoja del taller declara " + unidades
                    + " unidades y las filas convertidas suman " + convertidas
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

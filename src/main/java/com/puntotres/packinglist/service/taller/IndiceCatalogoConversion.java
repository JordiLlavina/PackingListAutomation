package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.puntotres.packinglist.service.ApcPedidoExcel;
import com.puntotres.packinglist.service.etiquetas.AmiPedidoExcel;

/**
 * El pedido del cliente como texto para Claude, indexado por lo que escribe
 * EL TALLER y no por cómo lo lista el cliente.
 *
 * El taller escribe el final de la referencia ("67008" por "PXCBC-F67008"),
 * el NOMBRE del color ("Negro", "Gris"), la destinación abreviada y el
 * código de pedido de tres cifras. Un catálogo con una línea por fila del
 * pedido obliga al modelo a buscar ese sufijo entre setenta líneas y no le
 * dice nunca qué código es "Negro". Aquí cada pregunta que se hace al leer
 * una fila tiene su bloque: para APC, la tabla de colores del cliente y los
 * artículos agrupados por sufijo con sus líneas de pedido (código ·
 * destinación · color); para AMI, cada referencia con cómo la escriben los
 * talleres (sin puntos ni ceros), sus colores con código y nombre y sus POs.
 *
 * Es texto compacto y no JSON a propósito: un array con las claves repetidas
 * en cada fila cuesta el doble o el triple de tokens y no se lee mejor. Lo
 * que importa es la agrupación. <b>No lleva cantidades</b>, por lo mismo que
 * el catálogo de la extracción por imágenes: lo pedido y lo empaquetado
 * pueden diferir de verdad y eso tiene que llegar a la pantalla, no
 * cuadrarse por el camino.
 */
public final class IndiceCatalogoConversion {

    /** Tope de líneas de pedido: catorce veces el fichero real mayor (ver CatalogoPedido). */
    static final int MAXIMO_LINEAS = 1000;

    static final String SIN_NOMBRE = "(sin nombre en la tabla de colores)";

    /** El índice montado y los avisos de montarlo. */
    public record Indice(String texto, List<String> avisos) {

        public Indice {
            avisos = List.copyOf(avisos);
        }

        public static Indice vacio() {
            return new Indice("", List.of());
        }

        public boolean estaVacio() {
            return texto.isBlank();
        }
    }

    private IndiceCatalogoConversion() {
    }

    public static Indice para(PedidoParaConversion pedido) {
        if (pedido == null || pedido.estaVacio()) {
            return Indice.vacio();
        }
        if (pedido.apc().isPresent()) {
            return deApc(pedido.apc().get(), pedido.coloresCliente());
        }
        if (pedido.ami().isPresent()) {
            return deAmi(pedido.ami().get());
        }
        return Indice.vacio();
    }

    // --- APC ---

    /** Lo que se sabe de un artículo: su nombre, sus colores y sus líneas de pedido. */
    private record Articulo(String referencia, String designacion, Set<String> colores,
                            Set<String> lineas) {
    }

    private static Indice deApc(ApcPedidoExcel excel, Map<String, String> coloresCliente) {
        List<ApcPedidoExcel.LineaCatalogo> lineas = excel.catalogo();
        if (lineas.isEmpty()) {
            return new Indice("", List.of("El excel de pedido no tiene ninguna línea que "
                    + "enseñarle a la IA: se ha convertido sin contrastarlo"));
        }
        if (lineas.size() > MAXIMO_LINEAS) {
            return new Indice("", List.of("El excel de pedido tiene " + lineas.size()
                    + " líneas, demasiadas para enseñárselas a la IA: ¿es el pedido de esta "
                    + "temporada? Se ha convertido sin él"));
        }

        Map<String, Articulo> articulos = new LinkedHashMap<>();
        Set<String> codigosDeColor = new LinkedHashSet<>();
        for (ApcPedidoExcel.LineaCatalogo linea : lineas) {
            String referencia = linea.referencia().trim().toUpperCase(Locale.ROOT);
            Articulo articulo = articulos.computeIfAbsent(referencia, r -> new Articulo(r,
                    linea.designacion() == null ? "" : linea.designacion().trim(),
                    new LinkedHashSet<>(), new LinkedHashSet<>()));
            String color = linea.color() == null ? "" : linea.color().trim().toUpperCase(Locale.ROOT);
            if (!color.isEmpty()) {
                articulo.colores().add(color);
                codigosDeColor.add(color);
            }
            articulo.lineas().add((sufijo(linea.pedido()) + " " + limpio(linea.destino()) + " "
                    + color).trim());
        }

        // Por sufijo (lo que hay detrás del guion), que es lo que escribe el
        // taller; dos artículos con el mismo sufijo quedan juntos y a la vista.
        Map<String, List<Articulo>> porSufijo = new TreeMap<>();
        for (Articulo articulo : articulos.values()) {
            porSufijo.computeIfAbsent(sufijoDeArticulo(articulo.referencia()),
                    s -> new ArrayList<>()).add(articulo);
        }

        StringBuilder texto = new StringBuilder();
        texto.append("## Pedido de APC esta temporada (").append(articulos.size())
                .append(" artículos, ").append(lineas.size()).append(" líneas de pedido)\n\n");
        texto.append("Sirve para VALIDAR y TRADUCIR lo que lees (regla 9), no para decidir. "
                + "No trae cantidades a propósito.\n\n");
        texto.append("Colores del cliente (código = nombre): ");
        List<String> colores = new ArrayList<>();
        Set<String> todosLosCodigos = new LinkedHashSet<>(coloresCliente.keySet());
        todosLosCodigos.addAll(codigosDeColor);
        for (String codigo : todosLosCodigos) {
            String nombre = coloresCliente.getOrDefault(codigo, "");
            colores.add(codigo + " = " + (nombre.isEmpty() ? SIN_NOMBRE : nombre));
        }
        texto.append(String.join(" · ", colores)).append("\n\n");
        texto.append("Artículos agrupados por el FINAL de la referencia, que es lo que escribe el "
                + "taller (a veces sin la letra: \"67008\" es F67008). Debajo de cada artículo, "
                + "sus líneas de pedido: código de 3 cifras · destinación · color.\n\n");
        for (Map.Entry<String, List<Articulo>> grupo : porSufijo.entrySet()) {
            texto.append(grupo.getKey()).append('\n');
            for (Articulo articulo : grupo.getValue()) {
                texto.append("  ").append(articulo.referencia());
                if (!articulo.designacion().isEmpty()) {
                    texto.append(" \"").append(articulo.designacion()).append('"');
                }
                texto.append(" — colores ").append(articulo.colores().isEmpty() ? "-"
                        : String.join(", ", articulo.colores())).append('\n');
                texto.append("    ").append(String.join(" · ", articulo.lineas())).append('\n');
            }
        }
        return new Indice(texto.toString(), List.of());
    }

    /** "PXCBC-F67008" → "F67008"; sin guion, la referencia entera. */
    static String sufijoDeArticulo(String referencia) {
        int guion = referencia.lastIndexOf('-');
        return guion < 0 ? referencia : referencia.substring(guion + 1);
    }

    /** Las tres últimas cifras del Document d'achat, que es lo que escribe el taller. */
    static String sufijo(String pedido) {
        String limpio = pedido == null ? "" : pedido.trim();
        return limpio.length() <= 3 ? limpio : limpio.substring(limpio.length() - 3);
    }

    // --- AMI ---

    private record ReferenciaAmi(Set<String> colores, Set<String> tallas, Set<String> pos) {
    }

    private static Indice deAmi(AmiPedidoExcel excel) {
        List<AmiPedidoExcel.LineaCatalogo> lineas = excel.catalogo();
        if (lineas.isEmpty()) {
            return new Indice("", List.of("El excel de pedido no tiene ninguna línea que "
                    + "enseñarle a la IA: se ha convertido sin contrastarlo"));
        }
        if (lineas.size() > MAXIMO_LINEAS) {
            return new Indice("", List.of("El excel de pedido tiene " + lineas.size()
                    + " líneas, demasiadas para enseñárselas a la IA: ¿es el pedido de esta "
                    + "temporada? Se ha convertido sin él"));
        }
        Map<String, ReferenciaAmi> referencias = new LinkedHashMap<>();
        for (AmiPedidoExcel.LineaCatalogo linea : lineas) {
            ReferenciaAmi referencia = referencias.computeIfAbsent(
                    linea.referencia().trim().toUpperCase(Locale.ROOT),
                    r -> new ReferenciaAmi(new LinkedHashSet<>(), new LinkedHashSet<>(),
                            new LinkedHashSet<>()));
            String color = (limpio(linea.colorCode()) + " = " + limpio(linea.colorNombre())).trim();
            referencia.colores().add(color.endsWith("=") ? color.substring(0, color.length() - 1).trim()
                    : color);
            if (!limpio(linea.talla()).isEmpty()) {
                referencia.tallas().add(limpio(linea.talla()));
            }
            String po = (limpio(linea.poNumerico()) + " " + limpio(linea.poSufijo())).trim();
            if (!po.isEmpty()) {
                referencia.pos().add(po);
            }
        }

        StringBuilder texto = new StringBuilder();
        texto.append("## Pedido de AMI esta temporada (").append(referencias.size())
                .append(" referencias)\n\n");
        texto.append("Sirve para VALIDAR y TRADUCIR lo que lees (regla 9), no para decidir. "
                + "No trae cantidades a propósito.\n\n");
        texto.append("Una línea por referencia: referencia (y entre paréntesis cómo la escriben "
                + "a veces los talleres, sin puntos ni ceros) | colores, código = nombre | "
                + "tallas | POs con su sufijo de destinación (CH = CHINA, JP = JAPAN, sin "
                + "sufijo = PARIS, PRESS = marketing)\n\n");
        for (Map.Entry<String, ReferenciaAmi> entrada : referencias.entrySet()) {
            ReferenciaAmi referencia = entrada.getValue();
            texto.append(entrada.getKey()).append(" (").append(formaCorta(entrada.getKey()))
                    .append(") | ").append(String.join(", ", referencia.colores()))
                    .append(" | ").append(referencia.tallas().isEmpty() ? "-"
                            : String.join(", ", referencia.tallas()))
                    .append(" | ").append(referencia.pos().isEmpty() ? "-"
                            : String.join(", ", referencia.pos()))
                    .append('\n');
        }
        return new Indice(texto.toString(), List.of());
    }

    /**
     * "ULL163.AL0052" → "ULL163AL52": sin puntos y sin ceros a la izquierda
     * de cada grupo de cifras, que es como la abrevian los talleres. Es la
     * misma forma con la que {@link AjustadorConversion} las casa.
     */
    static String formaCorta(String referencia) {
        StringBuilder corta = new StringBuilder();
        String limpia = referencia.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        int i = 0;
        while (i < limpia.length()) {
            char c = limpia.charAt(i);
            if (Character.isDigit(c)) {
                int fin = i;
                while (fin < limpia.length() && Character.isDigit(limpia.charAt(fin))) {
                    fin++;
                }
                String cifras = limpia.substring(i, fin).replaceFirst("^0+(?=\\d)", "");
                corta.append(cifras);
                i = fin;
            } else {
                corta.append(c);
                i++;
            }
        }
        return corta.toString();
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }
}

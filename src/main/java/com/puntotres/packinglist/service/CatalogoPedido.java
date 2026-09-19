package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.service.etiquetas.AmiPedidoExcel;

/**
 * El listado de lo que el cliente ha pedido esta temporada, en texto plano,
 * para enseñárselo a Claude mientras transcribe las hojas manuscritas.
 *
 * <b>Por qué existe:</b> leyendo caligrafía sin nada con lo que contrastar,
 * el modelo no tiene forma de decidir entre un 0 y un 6, entre "0014" y
 * "0015", ni de saber dónde acaba el nombre del modelo y empieza el color
 * ("sac Le Neige CLOU CAMEL"). Esa última separación es, según el análisis de
 * las hojas reales, la más difícil de toda la hoja de APC y no se puede
 * resolver sin el catálogo de colores del cliente. Con el listado delante, la
 * duda se resuelve contra un dato real.
 *
 * <b>Lo que NO lleva, a propósito:</b> cantidades. Lo pedido y lo empaquetado
 * pueden diferir de verdad —una entrega parcial, un pedido que se sirve en dos
 * envíos— y la regla 4 del prompt es explícita en que un descuadre es una
 * discrepancia real que el operario tiene que ver, no algo que se cuadre por
 * el camino. Enseñar las cantidades invitaría justo a eso.
 *
 * <b>Lo que sí lleva y por qué es seguro:</b> referencias, colores, tallas y
 * números de pedido, que son identificadores. El riesgo aquí es el contrario
 * —que el modelo encaje a la fuerza en el catálogo algo que de verdad no está
 * en él, como una reposición o una muestra—, y de eso se encarga la regla 15
 * del prompt, que obliga a transcribir lo escrito y avisar.
 *
 * El texto es el mismo para el camino manual de
 * docs/Packing Lists/prompts-para-copiar.md: quien lo use a mano puede pegar
 * este listado igual que lo manda la aplicación.
 */
public final class CatalogoPedido {

    /**
     * Tope de líneas del catálogo. Los ficheros reales dan 50 (AMI) y 70
     * (APC) líneas agrupadas, así que esto es catorce veces el mayor: si se
     * pasa, lo que hay subido no es un pedido de temporada y mandarlo entero
     * llenaría la petición de tokens sin ayudar a leer nada.
     */
    static final int MAXIMO_LINEAS = 1000;

    /** Lo que se escribe cuando el fichero no trae ese dato en esa fila. */
    private static final String SIN_DATO = "-";

    /**
     * El catálogo montado: el texto que viaja en la petición (vacío si no hay
     * ninguno) y los avisos para la pantalla de revisión.
     */
    public record Catalogo(String texto, List<String> avisos) {

        public Catalogo {
            avisos = List.copyOf(avisos);
        }

        public static Catalogo vacio() {
            return new Catalogo("", List.of());
        }

        public static Catalogo sinCatalogo(String aviso) {
            return new Catalogo("", List.of(aviso));
        }

        public boolean estaVacio() {
            return texto.isBlank();
        }
    }

    private CatalogoPedido() {
    }

    /**
     * El catálogo del cliente, o uno vacío si no hay excel de pedido o la
     * plantilla no usa ninguno.
     *
     * Sin excel <b>no avisa</b>: el usuario ya sabe que no lo ha subido y
     * quien lo necesita para otra cosa (completar pedidos de APC, las
     * etiquetas de AMI) ya avisa por su cuenta. Un excel que está pero no se
     * deja leer sí avisa: eso es una sorpresa.
     */
    public static Catalogo para(TipoPlantilla plantilla, byte[] excelPedido) {
        if (plantilla == null || excelPedido == null || excelPedido.length == 0) {
            return Catalogo.vacio();
        }
        try {
            return switch (plantilla) {
                case AMI -> deAmi(AmiPedidoExcel.desdeBytes(excelPedido).catalogo());
                case APC -> deApc(ApcPedidoExcel.desdeBytes(excelPedido).catalogo());
                // Las plantillas genéricas no tienen excel de pedido: no hay
                // catálogo que montar ni nada que avisar.
                case GENERIC -> Catalogo.vacio();
            };
        } catch (Exception e) {
            return Catalogo.sinCatalogo("No se ha podido leer el excel de pedido para "
                    + "contrastar la lectura de las hojas (" + e.getMessage() + "): "
                    + "se han transcrito sin él");
        }
    }

    /**
     * AMI: una línea por referencia y color, con todas sus tallas y todos sus
     * pedidos. El color va con código y nombre porque el operario escribe
     * tanto "2221" como "chocolat", y el PO con su sufijo de destinación
     * ("07704 CH"), que es como está en el fichero.
     */
    private static Catalogo deAmi(List<AmiPedidoExcel.LineaCatalogo> lineas) {
        Map<String, Set<String>> tallas = new LinkedHashMap<>();
        Map<String, Set<String>> pedidos = new LinkedHashMap<>();
        for (AmiPedidoExcel.LineaCatalogo linea : lineas) {
            String clave = linea.referencia() + '' + color(linea);
            tallas.computeIfAbsent(clave, x -> new TreeSet<>(POR_TALLA)).add(linea.talla());
            pedidos.computeIfAbsent(clave, x -> new LinkedHashSet<>()).add(po(linea));
        }

        List<String> filas = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entrada : tallas.entrySet()) {
            String[] clave = partir(entrada.getKey());
            filas.add(fila(clave[0], clave[1], unir(entrada.getValue()),
                    unir(pedidos.get(entrada.getKey()))));
        }
        return montar("referencia | color (código y nombre) | tallas | pedidos (PO)", filas);
    }

    /**
     * APC: una línea por artículo y pedido, que es la granularidad del
     * cliente —un "Document d'achat" es una destinación, un artículo y un
     * color, con una fila por talla—, así que el color y la destinación son
     * los mismos en todas las filas del grupo y las tallas se juntan.
     */
    private static Catalogo deApc(List<ApcPedidoExcel.LineaCatalogo> lineas) {
        Map<String, ApcPedidoExcel.LineaCatalogo> cabeceras = new LinkedHashMap<>();
        Map<String, Set<String>> tallas = new LinkedHashMap<>();
        for (ApcPedidoExcel.LineaCatalogo linea : lineas) {
            String clave = linea.referencia() + '' + linea.pedido();
            cabeceras.putIfAbsent(clave, linea);
            tallas.computeIfAbsent(clave, x -> new TreeSet<>(POR_TALLA)).add(linea.talla());
        }

        List<String> filas = new ArrayList<>();
        for (Map.Entry<String, ApcPedidoExcel.LineaCatalogo> entrada : cabeceras.entrySet()) {
            ApcPedidoExcel.LineaCatalogo linea = entrada.getValue();
            filas.add(fila(linea.referencia(), linea.pedido(), linea.destino(),
                    linea.designacion(), linea.color(), unir(tallas.get(entrada.getKey()))));
        }
        return montar("artículo (Article) | pedido (Document d'achat) | destinación | "
                + "modelo | color | tallas", filas);
    }

    private static Catalogo montar(String cabecera, List<String> filas) {
        if (filas.isEmpty()) {
            return Catalogo.sinCatalogo("El excel de pedido no tiene ninguna línea que "
                    + "enseñarle a Claude: las hojas se han transcrito sin contrastarlas");
        }
        if (filas.size() > MAXIMO_LINEAS) {
            return Catalogo.sinCatalogo("El excel de pedido tiene " + filas.size()
                    + " líneas distintas, demasiadas para enseñárselas a Claude al leer las "
                    + "hojas: ¿es el pedido de esta temporada? Se han transcrito sin él");
        }
        StringBuilder texto = new StringBuilder("""
                ## Catálogo del pedido de esta temporada

                Lo que el cliente ha pedido esta temporada, una línea por artículo. Es la
                ayuda de la regla 15: sirve para resolver dudas de caligrafía, NO es la
                lista de lo que tiene que salir. No trae cantidades a propósito.

                """);
        texto.append(cabecera).append('\n');
        for (String fila : filas) {
            texto.append(fila).append('\n');
        }
        return new Catalogo(texto.toString(), List.of());
    }

    private static String fila(String... campos) {
        List<String> limpios = new ArrayList<>();
        for (String campo : campos) {
            limpios.add(campo == null || campo.isBlank() ? SIN_DATO : campo.trim());
        }
        return String.join(" | ", limpios);
    }

    /** "2221 CHOCOLATE BROWN", o solo lo que haya de los dos. */
    private static String color(AmiPedidoExcel.LineaCatalogo linea) {
        return (linea.colorCode() + " " + linea.colorNombre()).trim();
    }

    /** "07704 CH", o el número solo cuando la destinación es la de por defecto. */
    private static String po(AmiPedidoExcel.LineaCatalogo linea) {
        return linea.poSufijo() == null || linea.poSufijo().isBlank()
                ? linea.poNumerico()
                : linea.poNumerico() + " " + linea.poSufijo();
    }

    private static String unir(Set<String> valores) {
        List<String> conDato = valores.stream().filter(v -> v != null && !v.isBlank()).toList();
        return conDato.isEmpty() ? SIN_DATO : String.join(", ", conDato);
    }

    /**
     * Las tallas son números escritos ("75", "100") y ordenarlas como texto
     * las deja en "100, 75, 80", que se lee como un error del fichero. Lo que
     * no sea un número (la "U" de los bolsos, "TU") va después, por orden.
     */
    private static final Comparator<String> POR_TALLA = Comparator
            .comparingInt(CatalogoPedido::valorDeTalla)
            .thenComparing(Comparator.naturalOrder());

    private static int valorDeTalla(String talla) {
        try {
            return Integer.parseInt(talla.trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static String[] partir(String clave) {
        int separador = clave.indexOf('');
        return new String[] {clave.substring(0, separador), clave.substring(separador + 1)};
    }
}

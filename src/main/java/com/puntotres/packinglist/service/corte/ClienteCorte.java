package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que cambia de un cliente a otro en los documentos del corte: cómo se
 * lee su excel de pedido y dónde lleva la piel su referencia. Se despacha por
 * clave de cliente, como las etiquetas; un cliente sin implementación sale
 * "en desarrollo" en la pantalla.
 */
public interface ClienteCorte {

    /** Clave del cliente en {@code packing-list.clientes} (AMI, APC). */
    String clave();

    /**
     * Lanza IllegalArgumentException con un mensaje en español si el fichero
     * no es el pedido de este cliente.
     */
    PedidoCorte leerPedido(byte[] excel) throws IOException;

    /**
     * Parte una referencia en modelo y piel con la regla del cliente. La usa
     * también la lectura de las fotos: una carpeta "ULL027.AL103" es el modelo
     * ULL027.
     */
    ReferenciaCorte partir(String referencia);

    /**
     * ¿Se corta esta referencia? Lo que no se corta (los cinturones de AMI)
     * no sale ni del pedido ni del excel de producción.
     */
    default boolean seCorta(String referencia) {
        return true;
    }

    /**
     * Un color tal como lo escribe el excel de producción ("LZZ NEGRO",
     * "0014 NOIR") partido en código y descripción, con la regla exacta de
     * cada cliente: nunca se busca un parecido en la Tabla colores Clientes.
     * Sin regla, o si el texto no la cumple, todo es descripción y el color
     * se identifica por ella. Sale con 0 bolsos: los pone quien lee la fila.
     */
    default ColorCorte colorDeProduccion(String texto) {
        return new ColorCorte("", texto, 0);
    }

    /**
     * Parte un color de producción con un patrón de dos grupos, código y
     * descripción; si no casa, todo es descripción.
     */
    static ColorCorte colorConPatron(String texto, Pattern patron) {
        String limpio = texto == null ? "" : texto.trim();
        Matcher partes = patron.matcher(limpio);
        if (!partes.matches()) {
            return new ColorCorte("", limpio, 0);
        }
        return new ColorCorte(partes.group(1), partes.group(2) == null ? "" : partes.group(2), 0);
    }

    /**
     * Todos los colores del excel de pedido, código → descripción (vacía si
     * el pedido no la trae), en el orden en que aparecen. Son TODOS, también
     * los de lo que el corte deja fuera (los cinturones de AMI): es lo que
     * alimenta la Tabla colores Clientes, que vale para cualquier documento.
     * Lanza IllegalArgumentException si el fichero no es el pedido de este
     * cliente.
     */
    Map<String, String> coloresDelPedido(byte[] excel) throws IOException;

    /**
     * Las referencias de piel del excel de pedido, sin repetir y por orden de
     * aparición: la parte de la referencia que es la piel, partida con la
     * regla del cliente (AL0201 en ULL027.AL0201; PXBHZ en PXBHZ-H65077).
     * Como los colores, son TODAS, también las de los cinturones de AMI. Lanza
     * IllegalArgumentException si el fichero no es el pedido de este cliente.
     */
    Set<String> pielesDelPedido(byte[] excel) throws IOException;

    /** Las pieles de unas líneas de pedido, con la regla de partir del cliente. */
    static Set<String> pieles(List<LineaCorte> lineas, Function<String, ReferenciaCorte> partir) {
        Set<String> pieles = new LinkedHashSet<>();
        for (LineaCorte linea : lineas) {
            String piel = partir.apply(ReferenciaCorte.normalizar(linea.referencia())).piel();
            if (!piel.isEmpty()) {
                pieles.add(piel);
            }
        }
        return pieles;
    }

    /** Código → la primera descripción no vacía que traigan sus líneas, en orden de aparición. */
    static Map<String, String> colores(List<LineaCorte> lineas) {
        Map<String, String> colores = new LinkedHashMap<>();
        for (LineaCorte linea : lineas) {
            if (linea.codigoColor().isEmpty()) {
                continue;
            }
            String codigo = ReferenciaCorte.normalizar(linea.codigoColor());
            colores.merge(codigo, linea.descripcionColor(),
                    (anterior, nueva) -> anterior.isEmpty() ? nueva : anterior);
        }
        return colores;
    }
}

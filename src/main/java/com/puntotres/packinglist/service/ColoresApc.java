package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.puntotres.packinglist.model.CajaData;

/**
 * Deja el color de cada caja de APC como lo escribe el cliente en su packing
 * list: <b>código-nombre</b> ("LZZ-NOIR", "CAB-CAMEL"), con el nombre de la
 * tabla de colores del cliente (pantalla Colores y Pieles de Cliente).
 *
 * Antes el color llegaba al excel tal como se había leído de la hoja
 * manuscrita o como lo había tecleado el taller ("CLOU CAMEL", "LZZ Negro",
 * "LZZ" a secas), porque nada lo cruzaba con la tabla. Es el mismo reparto que
 * en la conversión del excel de taller ({@code AjustadorConversion}): el
 * modelo LEE y decide qué color de la tabla es el que apunta la hoja (pasar
 * "camel" o "negro" a su código es cosa suya, por parecido); aquí, en Java,
 * solo se hace lo que tiene una única respuesta:
 * <ul>
 * <li>reconocer el código: el color entero ("LZZ"), su primera palabra
 *     ("LZZ Negro", "LZZ-NOIR") o el nombre EXACTO de la tabla ("NOIR");</li>
 * <li>escribirlo con el nombre de la tabla;</li>
 * <li>comprobar que es uno de los colores del {@code Document d'achat} de la
 *     caja, y avisar si no: o el color o el pedido están mal.</li>
 * </ul>
 * Lo que no se reconoce se queda como llegó, con un aviso: un color encajado
 * a la fuerza acabaría en un documento del cliente.
 *
 * Es idempotente ("LZZ-NOIR" sale "LZZ-NOIR"), así que puede correr al
 * importar y otra vez al generar, para lo que se haya tecleado en la revisión.
 */
public final class ColoresApc {

    private ColoresApc() {
    }

    /**
     * @param tabla       código → nombre de la tabla de colores del cliente
     *                    (nombre vacío si no se sabe)
     * @param excelPedido el excel de pedido, o null; sin él no se comprueba
     *                    el color contra el pedido
     * @return los avisos, uno por clase y no uno por caja
     */
    public static List<String> normalizar(List<EnvioImportado.DestinoImportado> destinos,
                                          Map<String, String> tabla, byte[] excelPedido) {
        Map<String, String> nombres = new LinkedHashMap<>();
        if (tabla != null) {
            tabla.forEach((codigo, nombre) -> {
                if (codigo != null && !codigo.isBlank()) {
                    nombres.put(clave(codigo), nombre == null ? "" : clave(nombre));
                }
            });
        }
        ApcPedidoExcel pedido = leer(excelPedido);
        Set<String> codigos = new LinkedHashSet<>(nombres.keySet());
        if (pedido != null) {
            for (ApcPedidoExcel.LineaCatalogo linea : pedido.catalogo()) {
                if (linea.color() != null && !linea.color().isBlank()) {
                    codigos.add(clave(linea.color()));
                }
            }
        }

        Set<String> sinReconocer = new LinkedHashSet<>();
        Set<String> sinNombre = new LinkedHashSet<>();
        Map<String, String> noCuadran = new LinkedHashMap<>();
        for (EnvioImportado.DestinoImportado destino : destinos) {
            for (CajaData caja : destino.getDestino().getCajas()) {
                String color = caja.getCodigoColor() == null ? "" : clave(caja.getCodigoColor());
                if (color.isEmpty()) {
                    continue;
                }
                String codigo = codigoDe(color, nombres, codigos);
                if (codigo == null) {
                    sinReconocer.add(color);
                    continue;
                }
                String nombre = nombres.getOrDefault(codigo, "");
                if (nombre.isEmpty()) {
                    // Sin nombre en la tabla no se pisa el que ya viniera
                    // escrito detrás del código ("CAB-CAMEL" se queda).
                    if (color.equals(codigo)) {
                        sinNombre.add(codigo);
                    }
                } else {
                    caja.setCodigoColor(codigo + "-" + nombre);
                }
                comprobarContraElPedido(caja, codigo, pedido, noCuadran);
            }
        }

        List<String> avisos = new ArrayList<>();
        if (!sinReconocer.isEmpty()) {
            avisos.add("Colores que no están en la tabla de colores de APC: "
                    + entreComillas(sinReconocer) + ". Salen en el packing list como se han "
                    + "leído: corrígelos en la columna Color de la revisión (código-nombre, "
                    + "como LZZ-NOIR) o añádelos en Colores y Pieles de Cliente");
        }
        if (!sinNombre.isEmpty()) {
            avisos.add("Colores sin nombre en la tabla de colores de APC: "
                    + String.join(", ", sinNombre) + ". Salen solo con el código y el cliente "
                    + "los espera como código-nombre (LZZ-NOIR): ponles nombre en Colores y "
                    + "Pieles de Cliente");
        }
        avisos.addAll(noCuadran.values());
        return avisos;
    }

    /**
     * El código de color que dice el texto, o null si no dice ninguno que se
     * conozca. Solo por coincidencia exacta: el parecido es cosa del modelo.
     */
    static String codigoDe(String color, Map<String, String> nombres, Set<String> codigos) {
        if (codigos.contains(color)) {
            return color;
        }
        // El código con algo detrás: "LZZ NEGRO", "LZZ-NOIR", "GAU/CAMEL".
        String primera = color.split("[\\s\\-/]+", 2)[0];
        if (codigos.contains(primera)) {
            return primera;
        }
        // El nombre de la tabla, si es de un solo código.
        String unico = null;
        for (Map.Entry<String, String> entrada : nombres.entrySet()) {
            if (!entrada.getValue().isEmpty() && entrada.getValue().equals(color)) {
                if (unico != null) {
                    return null;
                }
                unico = entrada.getKey();
            }
        }
        return unico;
    }

    private static void comprobarContraElPedido(CajaData caja, String codigo,
                                                ApcPedidoExcel pedido,
                                                Map<String, String> noCuadran) {
        String numero = caja.getNumeroPedido();
        if (pedido == null || numero == null || numero.isBlank()) {
            return;
        }
        Set<String> delPedido = pedido.coloresDe(numero);
        if (delPedido.isEmpty() || delPedido.contains(codigo)) {
            return;
        }
        noCuadran.putIfAbsent(caja.getReferencia() + "|" + numero + "|" + codigo,
                "El pedido " + numero + " de " + caja.getReferencia() + " es en "
                        + String.join("/", delPedido) + " y el envío dice " + codigo
                        + ": o el color o el pedido están mal. Se deja lo leído; compruébalo "
                        + "en la revisión");
    }

    private static ApcPedidoExcel leer(byte[] excelPedido) {
        if (excelPedido == null || excelPedido.length == 0) {
            return null;
        }
        try {
            return ApcPedidoExcel.desdeBytes(excelPedido);
        } catch (Exception ilegible) {
            // Que no se deja leer ya lo avisa la completación de pedidos.
            return null;
        }
    }

    private static String entreComillas(Set<String> textos) {
        return String.join(", ", textos.stream().map(texto -> "'" + texto + "'").toList());
    }

    private static String clave(String texto) {
        return texto.trim().toUpperCase(Locale.ROOT);
    }
}

package com.puntotres.packinglist.service.etiquetas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Índice en memoria del excel de pedido de la temporada de AMI (el que sube
 * el usuario en el Paso 2, ej. "AMI EAN H26.xlsx").
 *
 * La hoja buena es la primera cuyo nombre empieza por "EAN" (la temporada
 * cambia: EAN H26, EAN E27...); el libro trae más hojas (bolsitas, copias
 * por artículo) que se ignoran. Las columnas se localizan por el texto de
 * la cabecera de la fila 1, no por posición.
 *
 * La columna PO codifica la destinación: "NNNNN CH" (China), "NNNNN JP"
 * (Japan) o un número sin sufijo (France). El order number de la etiqueta
 * es siempre la parte numérica con padding a 5 dígitos.
 *
 * De aquí salen dos cosas con reglas distintas a propósito:
 * <ul>
 * <li><b>order number y color code</b>: filtrando por ARTICLE + sufijo de PO y
 * prefiriendo el COLORIS, con caída a la primera fila candidata. Un color code
 * aproximado es aceptable.</li>
 * <li><b>EAN13 y EAN128</b>: solo con clave EXACTA ARTICLE + COLORIS + TAILLE +
 * sufijo de PO, que en el fichero real identifica una única fila. Imprimir un
 * código de barras equivocado es peor que no imprimirlo.</li>
 * </ul>
 *
 * La lectura de bajo nivel (localizar la hoja, resolver columnas, leer
 * celdas) está en HojaEan, compartida con las etiquetas de artículo. Las
 * filas ocultas se leen como las demás: ocultar es estado de vista, y el
 * fichero real del cliente viene con casi todas ocultas por un filtro.
 */
public class AmiPedidoExcel {

    /** Talla de lo que no es cinturón en la columna TAILLE del pedido. */
    private static final String TALLA_UNICA = "U";

    /**
     * Una coincidencia del pedido. colorCode es SOLO el COLORIS ("221"), que
     * es lo que la plantilla nueva quiere en la celda COLOR CODE de la
     * etiqueta; colorCompleto añade el libellé ("221 DARK COFFEE") y es lo que
     * va a la imagen compuesta y a la hoja de códigos extra, igual que en la
     * etiqueta de artículo. ean13/ean128 son null cuando no se pueden dar (sin
     * fila exacta, sin columna, EAN13 inválido); avisosEan explica por qué, sin
     * contexto de caja ni destinación: lo añade quien llama.
     */
    public record FilaPedido(String orderNumber, String colorCode, String colorCompleto,
                             String ean13, String ean128, List<String> avisosEan) {

        public FilaPedido {
            avisosEan = List.copyOf(avisosEan);
        }
    }

    private record FilaCruda(String madeIn, String article, String coloris, String libelle,
                             String taille, String poNumerico, String poSufijo,
                             String ean13, String ean128, int commande) {
    }

    /**
     * Una línea de pedido vista desde la entrada por taller: cuántas unidades
     * se han pedido de un artículo y para qué pedido. El sufijo del PO es la
     * destinación ("CH", "JP", o null para la de por defecto), pero traducirlo
     * a un nombre no es cosa de este lector: eso lo dice la configuración.
     */
    public record Comanda(String poNumerico, String poSufijo, int cantidad) {
    }

    private final List<FilaCruda> filas;
    private final List<String> avisos;

    private AmiPedidoExcel(List<FilaCruda> filas, List<String> avisos) {
        this.filas = List.copyOf(filas);
        this.avisos = List.copyOf(avisos);
    }

    public static AmiPedidoExcel desdeBytes(byte[] contenido) throws IOException {
        try (HojaEan hoja = HojaEan.abrir(contenido)) {
            int colArticle = hoja.columna("ARTICLE");
            int colColoris = hoja.columna("COLORIS");
            int colLibelle = hoja.columna("LIBELL", "Libellé coloris");
            int colTaille = hoja.columna("TAILLE");
            int colPo = hoja.columna("PO");
            int colMadeIn = hoja.columnaOpcional("MADE IN");
            int colEan13 = hoja.columnaOpcional("EAN13");
            int colEan128 = hoja.columnaOpcional("EAN128");
            // Solo la usa la entrada por taller, para saber cuánto pide el
            // cliente de cada cosa. Las etiquetas nunca la han necesitado.
            int colCommande = hoja.columnaOpcional("COMMAND");

            List<String> avisos = new ArrayList<>();
            if (colEan13 < 0) {
                avisos.add("El excel de pedido no tiene la columna 'EAN13': "
                        + "las etiquetas van sin ese código de barras");
            }
            if (colEan128 < 0) {
                avisos.add("El excel de pedido no tiene la columna 'EAN128': "
                        + "las etiquetas van sin ese código de barras");
            }
            if (colMadeIn < 0) {
                avisos.add("El excel de pedido no tiene la columna 'Made in': "
                        + "no se comprueba el país del EAN128");
            }

            List<FilaCruda> filas = new ArrayList<>();
            for (int i = hoja.primeraFilaDatos(); i <= hoja.ultimaFila(); i++) {
                String article = hoja.texto(i, colArticle);
                String po = hoja.texto(i, colPo);
                if (article.isBlank() || po.isBlank()) {
                    continue;
                }
                String numerico = po.replaceAll("[^0-9]", "");
                if (numerico.isBlank()) {
                    continue;
                }
                String sufijo = po.replaceAll("[0-9\\s]", "").toUpperCase(Locale.ROOT);
                filas.add(new FilaCruda(
                        textoDe(hoja, i, colMadeIn).toUpperCase(Locale.ROOT),
                        article.trim().toUpperCase(Locale.ROOT),
                        hoja.texto(i, colColoris).trim(),
                        hoja.texto(i, colLibelle).trim(),
                        hoja.texto(i, colTaille).trim(),
                        String.format("%05d", Long.parseLong(numerico)),
                        sufijo.isBlank() ? null : sufijo,
                        textoDe(hoja, i, colEan13),
                        textoDe(hoja, i, colEan128),
                        enteroDe(hoja, i, colCommande)));
            }
            return new AmiPedidoExcel(filas, avisos);
        }
    }

    /** Avisos de nivel de libro: columnas de las que se ha prescindido. */
    public List<String> avisos() {
        return avisos;
    }

    /**
     * Lo que el cliente ha pedido de un artículo, agrupado por número de
     * pedido: una entrada por PO, con sus unidades sumadas.
     *
     * Las tallas NO se suman entre sí: cada talla de un cinturón es un
     * artículo distinto con su propio código de barras, y sumarlas mandaría
     * al almacén una cantidad que no corresponde a nada. Sí se suman varias
     * líneas de pedido del mismo artículo y el mismo PO, que es como el ERP
     * parte una entrega en varias fechas.
     *
     * La lista sale en el orden en que los pedidos aparecen en el fichero,
     * para que el resultado sea siempre el mismo.
     */
    public List<Comanda> comandasDe(String referencia, String codigoColor, String talla) {
        String ref = referencia == null ? "" : referencia.trim().toUpperCase(Locale.ROOT);
        String color = codigoColor == null ? "" : codigoColor.trim();
        String tallaBuscada = talla == null || talla.isBlank() ? TALLA_UNICA : talla.trim();

        Map<String, Comanda> porPedido = new LinkedHashMap<>();
        for (FilaCruda fila : filas) {
            if (!fila.article().equals(ref)
                    || !fila.coloris().equalsIgnoreCase(color)
                    || !fila.taille().equalsIgnoreCase(tallaBuscada)) {
                continue;
            }
            porPedido.merge(fila.poNumerico(),
                    new Comanda(fila.poNumerico(), fila.poSufijo(), fila.commande()),
                    (previa, nueva) -> new Comanda(previa.poNumerico(), previa.poSufijo(),
                            previa.cantidad() + nueva.cantidad()));
        }
        return List.copyOf(porPedido.values());
    }

    /** Todas las comandas del fichero, sin filtrar. Para diagnóstico y tests. */
    public List<Comanda> comandasTodas() {
        return filas.stream()
                .map(fila -> new Comanda(fila.poNumerico(), fila.poSufijo(), fila.commande()))
                .toList();
    }

    /**
     * Los sufijos de PO distintos que aparecen en el fichero, sin el vacío.
     * Sirven para saber a qué destinaciones va esta temporada.
     */
    public List<String> sufijosPo() {
        return filas.stream()
                .map(FilaCruda::poSufijo)
                .filter(sufijo -> sufijo != null && !sufijo.isBlank())
                .distinct()
                .toList();
    }

    /**
     * Busca la fila del pedido para una referencia, color, talla y
     * destinación. talla null o vacía = talla única ("U"), que es lo que
     * traen en el pedido los bolsos.
     */
    public Optional<FilaPedido> buscar(String referencia, String codigoColor,
                                       String talla, String sufijoPo) {
        String ref = referencia == null ? "" : referencia.trim().toUpperCase(Locale.ROOT);
        String color = codigoColor == null ? "" : codigoColor.trim();
        List<FilaCruda> candidatas = filas.stream()
                .filter(fila -> fila.article().equals(ref))
                .filter(fila -> sufijoPo == null
                        ? fila.poSufijo() == null
                        : sufijoPo.equalsIgnoreCase(fila.poSufijo()))
                .toList();
        if (candidatas.isEmpty()) {
            return Optional.empty();
        }

        // Order number y color code: como siempre, con caída a la primera.
        FilaCruda elegida = candidatas.stream()
                .filter(fila -> fila.coloris().equalsIgnoreCase(color))
                .findFirst()
                .orElse(candidatas.get(0));
        String colorCode = elegida.coloris();
        String colorCompleto = elegida.libelle().isBlank()
                ? colorCode
                : colorCode + " " + elegida.libelle();

        // Los EAN, solo con clave exacta.
        String tallaBuscada = talla == null || talla.isBlank() ? TALLA_UNICA : talla.trim();
        List<FilaCruda> exactas = candidatas.stream()
                .filter(fila -> fila.coloris().equalsIgnoreCase(color))
                .filter(fila -> fila.taille().equalsIgnoreCase(tallaBuscada))
                .toList();

        List<String> avisosEan = new ArrayList<>();
        String ean13 = null;
        String ean128 = null;
        if (exactas.size() == 1) {
            FilaCruda exacta = exactas.get(0);
            ean13 = exacta.ean13().isBlank() ? null : exacta.ean13();
            if (ean13 != null && !CodigoBarrasEan13.esValido(ean13)) {
                avisosEan.add("el EAN13 '" + ean13 + "' del pedido no es un EAN-13 válido"
                        + " (13 dígitos con dígito de control): etiqueta sin ese código");
                ean13 = null;
            }
            ean128 = exacta.ean128().isBlank() ? null : exacta.ean128();
            String esperado = estructuraEsperada(exacta);
            if (ean128 != null && esperado != null && !esperado.equals(ean128)) {
                avisosEan.add("el EAN128 del pedido (" + ean128 + ") no cuadra con su EAN13,"
                        + " su PO y su 'Made in' (debería ser " + esperado + "):"
                        + " se imprime tal cual, pero revisar el fichero con el cliente");
            }
        } else if (exactas.isEmpty()) {
            avisosEan.add("el pedido no tiene fila de " + ref + " color '" + color
                    + "' talla '" + tallaBuscada + "' para "
                    + (sufijoPo == null ? "France" : sufijoPo)
                    + ": etiqueta sin EAN13 ni EAN128");
        } else {
            avisosEan.add("el pedido tiene " + exactas.size() + " filas de " + ref + " color '"
                    + color + "' talla '" + tallaBuscada + "' para "
                    + (sufijoPo == null ? "France" : sufijoPo)
                    + ": etiqueta sin EAN13 ni EAN128 para no elegir a ciegas");
        }
        return Optional.of(
                new FilaPedido(elegida.poNumerico(), colorCode, colorCompleto, ean13, ean128, avisosEan));
    }

    /**
     * El EAN128 del cliente es EAN13 + "00001" + PO a 8 dígitos + 16 ceros +
     * país ("ES" SPAIN, "MA" MOROCCO). Devuelve null si no se puede componer
     * (sin EAN13, sin 'Made in' o país desconocido): entonces no se comprueba.
     *
     * Sirve solo para AVISAR: el EAN128 que se imprime es siempre el de la
     * columna, verbatim. En el fichero real hay 8 filas que no cuadran y es el
     * código del cliente el que espera su escáner.
     */
    private static String estructuraEsperada(FilaCruda fila) {
        String pais = switch (fila.madeIn()) {
            case "SPAIN" -> "ES";
            case "MOROCCO" -> "MA";
            default -> null;
        };
        if (pais == null || fila.ean13().isBlank()) {
            return null;
        }
        return fila.ean13() + "00001"
                + String.format("%08d", Long.parseLong(fila.poNumerico()))
                + "0000000000000000" + pais;
    }

    /** Texto de una celda cuya columna puede no existir (-1 = "" sin leer). */
    private static String textoDe(HojaEan hoja, int fila, int columna) {
        return columna < 0 ? "" : hoja.texto(fila, columna).trim();
    }

    /**
     * Cantidad de una celda numérica. Sin columna o sin valor legible, cero:
     * una cantidad que no se entiende no se inventa, y quien la pida verá que
     * ese artículo no tiene pedido.
     */
    private static int enteroDe(HojaEan hoja, int fila, int columna) {
        String crudo = textoDe(hoja, fila, columna);
        if (crudo.isEmpty()) {
            return 0;
        }
        try {
            return new java.math.BigDecimal(crudo).intValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

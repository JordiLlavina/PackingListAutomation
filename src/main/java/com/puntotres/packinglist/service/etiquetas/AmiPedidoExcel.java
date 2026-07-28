package com.puntotres.packinglist.service.etiquetas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * La lectura de bajo nivel (localizar la hoja, resolver columnas, leer
 * celdas) está en HojaEan, compartida con las etiquetas de artículo.
 */
public class AmiPedidoExcel {

    /** Una coincidencia del pedido: order number (5 dígitos) y color code ("001 BLACK"). */
    public record FilaPedido(String orderNumber, String colorCode) {
    }

    private record FilaCruda(String article, String coloris, String libelle,
                             String poNumerico, String poSufijo) {
    }

    private final List<FilaCruda> filas;

    private AmiPedidoExcel(List<FilaCruda> filas) {
        this.filas = filas;
    }

    public static AmiPedidoExcel desdeBytes(byte[] contenido) throws IOException {
        try (HojaEan hoja = HojaEan.abrir(contenido)) {
            int colArticle = hoja.columna("ARTICLE");
            int colColoris = hoja.columna("COLORIS");
            int colLibelle = hoja.columna("LIBELL");
            int colPo = hoja.columna("PO");

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
                        article.trim().toUpperCase(Locale.ROOT),
                        hoja.texto(i, colColoris).trim(),
                        hoja.texto(i, colLibelle).trim(),
                        String.format("%05d", Long.parseLong(numerico)),
                        sufijo.isBlank() ? null : sufijo));
            }
            return new AmiPedidoExcel(filas);
        }
    }

    /**
     * Busca la fila del pedido para una referencia y destinación. Si hay
     * varias (cinturones: una por talla) da igual cuál: comparten PO y
     * color; se prefiere la que coincida en COLORIS con el color del JSON.
     */
    public Optional<FilaPedido> buscar(String referencia, String codigoColor, String sufijoPo) {
        String ref = referencia == null ? "" : referencia.trim().toUpperCase(Locale.ROOT);
        List<FilaCruda> candidatas = filas.stream()
                .filter(fila -> fila.article().equals(ref))
                .filter(fila -> sufijoPo == null
                        ? fila.poSufijo() == null
                        : sufijoPo.equalsIgnoreCase(fila.poSufijo()))
                .toList();
        if (candidatas.isEmpty()) {
            return Optional.empty();
        }
        FilaCruda elegida = candidatas.stream()
                .filter(fila -> fila.coloris().equalsIgnoreCase(codigoColor == null ? "" : codigoColor.trim()))
                .findFirst()
                .orElse(candidatas.get(0));
        String colorCode = elegida.libelle().isBlank()
                ? elegida.coloris()
                : elegida.coloris() + " " + elegida.libelle();
        return Optional.of(new FilaPedido(elegida.poNumerico(), colorCode));
    }
}

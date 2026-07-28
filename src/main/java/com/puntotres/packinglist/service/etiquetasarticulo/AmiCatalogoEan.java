package com.puntotres.packinglist.service.etiquetasarticulo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.puntotres.packinglist.service.etiquetas.HojaEan;

/**
 * Lee el excel de pedido de AMI y devuelve TODAS sus filas, sin
 * interpretarlas.
 *
 * Comparte con AmiPedidoExcel (etiquetas de caja) la lectura de bajo nivel
 * vía HojaEan, pero no su API: allí interesa buscar el PO de una referencia
 * concreta, aquí interesan todas las filas y el EAN13.
 *
 * Una fila del todo vacía se ignora en silencio (los excels del cliente
 * traen filas sueltas al final); una fila con datos pero sin ARTICLE o sin
 * PO se omite CON aviso, porque probablemente sea un error del fichero.
 */
public final class AmiCatalogoEan {

    private final String nombreHoja;
    private final List<FilaEan> filas;
    private final List<String> avisos;

    private AmiCatalogoEan(String nombreHoja, List<FilaEan> filas, List<String> avisos) {
        this.nombreHoja = nombreHoja;
        this.filas = List.copyOf(filas);
        this.avisos = List.copyOf(avisos);
    }

    public static AmiCatalogoEan desdeBytes(byte[] contenido) throws IOException {
        try (HojaEan hoja = HojaEan.abrir(contenido)) {
            int colMadeIn = hoja.columna("MADE IN");
            int colArticle = hoja.columna("ARTICLE");
            int colColoris = hoja.columna("COLORIS");
            int colLibelle = hoja.columna("LIBELL", "Libellé coloris");
            int colTaille = hoja.columna("TAILLE");
            int colPo = hoja.columna("PO");
            int colEan13 = hoja.columna("EAN13");

            List<FilaEan> filas = new ArrayList<>();
            List<String> avisos = new ArrayList<>();
            for (int i = hoja.primeraFilaDatos(); i <= hoja.ultimaFila(); i++) {
                String article = hoja.texto(i, colArticle).trim().toUpperCase(Locale.ROOT);
                String po = hoja.texto(i, colPo).trim();
                // Del todo vacía = ninguna columna trae nada: los excels del
                // cliente arrastran filas sueltas al final y esas se ignoran
                // sin ruido. Si trae CUALQUIER dato pero le falta ARTICLE o
                // PO, se avisa: es un error del fichero que alguien puede
                // arreglar.
                boolean vacia = article.isBlank() && po.isBlank()
                        && hoja.texto(i, colMadeIn).isBlank()
                        && hoja.texto(i, colColoris).isBlank()
                        && hoja.texto(i, colLibelle).isBlank()
                        && hoja.texto(i, colTaille).isBlank()
                        && hoja.texto(i, colEan13).isBlank();
                if (vacia) {
                    continue;
                }
                String numerico = po.replaceAll("[^0-9]", "");
                if (article.isBlank() || numerico.isBlank()) {
                    // La fila del excel se cuenta desde 1, no desde 0.
                    avisos.add("Fila " + (i + 1) + " del pedido sin "
                            + (article.isBlank() ? "ARTICLE" : "PO") + ": se omite");
                    continue;
                }
                String sufijo = po.replaceAll("[0-9\\s]", "").toUpperCase(Locale.ROOT);
                filas.add(new FilaEan(
                        hoja.texto(i, colMadeIn).trim().toUpperCase(Locale.ROOT),
                        article,
                        hoja.texto(i, colColoris).trim(),
                        hoja.texto(i, colLibelle).trim(),
                        hoja.texto(i, colTaille).trim(),
                        String.format("%05d", Long.parseLong(numerico)),
                        sufijo.isBlank() ? null : sufijo,
                        hoja.texto(i, colEan13).trim()));
            }
            return new AmiCatalogoEan(hoja.nombre(), filas, avisos);
        }
    }

    public List<FilaEan> filas() {
        return filas;
    }

    public List<String> avisos() {
        return avisos;
    }

    /**
     * "EAN H26" -> "H26", para el nombre de los ficheros generados. Vacío si
     * la hoja se llama solo "EAN": entonces la temporada la pone el usuario
     * desde la pantalla.
     */
    public Optional<String> temporada() {
        String resto = nombreHoja.substring("EAN".length()).trim();
        return resto.isBlank() ? Optional.empty() : Optional.of(resto.toUpperCase(Locale.ROOT));
    }
}

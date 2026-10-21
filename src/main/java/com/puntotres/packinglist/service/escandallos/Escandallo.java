package com.puntotres.packinglist.service.escandallos;

import java.util.Arrays;
import java.util.List;

/**
 * Un escandallo del ERP ya limpio: el bloque de cabecera del artículo y su
 * tabla de materiales. Se descarta todo lo demás que trae el listado del ERP
 * (precios, importes, tabla de fases y totales).
 *
 * Los escandallos son por modelo <em>y color</em>: dos ficheros distintos
 * pueden traer el mismo {@code modelo}. Por eso se guarda también el
 * {@code origen} (el nombre del fichero subido), que es lo único que
 * identifica al escandallo cuando hay que avisar de algo.
 */
public record Escandallo(String modelo, String descripcion, String color,
                         List<LineaEscandallo> lineas, String origen) {

    /**
     * El campo COLOR del ERP viene como «000    0014 NOIR»: delante los
     * códigos internos y al final la descripción, que es lo único que se
     * escribe en el excel y en el nombre de la hoja. Se quitan los grupos del
     * principio que llevan algún dígito —también un código con letras como
     * «A237», que AMI tiene—, y solo los del principio: un número dentro de la
     * descripción es parte de ella. Si no queda nada, el código <em>es</em> el
     * color y se devuelve tal cual. Vacío si no hay color.
     */
    public String descripcionColor() {
        if (color == null || color.isBlank()) {
            return "";
        }
        String[] partes = color.trim().split("\\s+");
        int inicio = 0;
        while (inicio < partes.length && partes[inicio].matches(".*\\d.*")) {
            inicio++;
        }
        String[] quedan = inicio < partes.length
                ? Arrays.copyOfRange(partes, inicio, partes.length)
                : partes;
        return String.join(" ", quedan);
    }
}

package com.puntotres.packinglist.service.embalaje;

import java.util.List;

/**
 * Los artículos que se pueden elegir en el formulario de «Info de embalaje»,
 * en el orden del fichero de pedido, y los avisos de la lectura (un sufijo de
 * PO sin destinación conocida, por ejemplo). Los avisos no bloquean: el
 * usuario corrige la destinación en el propio formulario.
 */
public record ArticulosEmbalaje(List<ArticuloEmbalaje> articulos, List<String> avisos) {

    public ArticulosEmbalaje {
        articulos = List.copyOf(articulos);
        avisos = List.copyOf(avisos);
    }
}

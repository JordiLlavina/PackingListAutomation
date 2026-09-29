package com.puntotres.packinglist.web;

import java.util.List;

import com.puntotres.packinglist.service.corte.ColorCorte;

/**
 * Una fila de la tabla de pieles lista para pintar: las combinaciones ya
 * vienen con una casilla por columna, y las fotos son los nombres de las de
 * su modelo, en el orden del desplegable.
 */
public record FilaPielesVista(int indice, boolean incluida, String referencia, String modelo, String piel,
                              List<ColorCorte> colores, String nombrePiel, String forro,
                              List<String> combinaciones, List<String> fotos, int fotoPrincipal) {
}

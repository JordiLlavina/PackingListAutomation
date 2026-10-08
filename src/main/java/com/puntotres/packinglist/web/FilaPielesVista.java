package com.puntotres.packinglist.web;

import java.util.List;

/**
 * Una referencia de la tabla de pieles lista para pintar: las combinaciones
 * ya vienen con una casilla por columna, y las fotos son los nombres de las
 * de su modelo, en el orden del desplegable. Se pinta en tantas filas como
 * colores tenga: lo que es de la referencia (nombres de piel, foto) una sola
 * vez, y lo que es de cada color (bolsos, colores de piel) en la suya.
 */
public record FilaPielesVista(int indice, boolean incluida, String referencia, String modelo, String piel,
                              List<ColorVista> colores, String nombrePiel, String forro,
                              List<String> combinaciones, List<String> fotos, int fotoPrincipal) {

    /**
     * Un color del bolso con sus bolsos y el color de cada piel. El código va
     * aparte porque es con lo que la pantalla reconoce "el mismo color" en
     * otros modelos al rellenar los colores del forro y de las combinaciones.
     */
    public record ColorVista(int indice, String codigo, String nombre, int bolsos, String colorPiel,
                             String colorForro, List<String> coloresCombinacion) {
    }
}

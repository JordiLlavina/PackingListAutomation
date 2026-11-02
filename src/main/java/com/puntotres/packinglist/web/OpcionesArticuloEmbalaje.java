package com.puntotres.packinglist.web;

import java.util.List;

import com.puntotres.packinglist.service.embalaje.ArticuloEmbalaje;

/**
 * El texto de cada opción del desplegable de artículos del formulario de
 * «Info de embalaje», con el nombre del bolso <b>alineado en columna</b>
 * (petición del usuario: «tabular» el desplegable).
 *
 * Un {@code <option>} no pinta tabuladores, así que la columna se hace a
 * mano: el desplegable va en letra de ancho fijo (clase
 * {@code .embalaje-articulo-select} del CSS) y la parte «referencia · color»
 * de cada opción se rellena por la derecha con espacios duros ({@code  })
 * hasta la más larga de la lista. Con espacios normales el navegador los
 * colapsaría en uno y la columna se perdería. Es cosa de la pantalla: el
 * artículo sigue teniendo su {@link ArticuloEmbalaje#etiqueta()} de una línea.
 */
final class OpcionesArticuloEmbalaje {

    /** El espacio que el navegador no colapsa dentro de un {@code <option>}. */
    static final char ESPACIO_DURO = ' ';

    private OpcionesArticuloEmbalaje() {
    }

    /** Un texto por artículo, en el mismo orden, con el nombre del bolso en la misma columna. */
    static List<String> textos(List<ArticuloEmbalaje> articulos) {
        int ancho = articulos.stream()
                .mapToInt(a -> a.referenciaYColor().length())
                .max().orElse(0);
        return articulos.stream().map(a -> texto(a, ancho)).toList();
    }

    private static String texto(ArticuloEmbalaje articulo, int ancho) {
        String prefijo = articulo.referenciaYColor();
        if (!articulo.tieneNombre()) {
            return prefijo;
        }
        return prefijo + String.valueOf(ESPACIO_DURO).repeat(ancho - prefijo.length())
                + ArticuloEmbalaje.SEPARADOR_NOMBRE.replace(' ', ESPACIO_DURO) + articulo.nombreBolso();
    }
}

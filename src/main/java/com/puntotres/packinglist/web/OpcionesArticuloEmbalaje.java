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
        return alinear(articulos.stream().map(ArticuloEmbalaje::referenciaYColor).toList(),
                articulos.stream().map(a -> a.tieneNombre() ? a.nombreBolso() : "").toList());
    }

    /**
     * La misma columna para cualquier lista: cada {@code prefijo} («referencia ·
     * color») rellenado con espacios duros hasta el más largo y, detrás, « — »
     * y su nombre, si tiene. La usa también el desplegable de artículos de las
     * Hojas de Producción, que es el mismo desplegable (petición del usuario).
     */
    static List<String> alinear(List<String> prefijos, List<String> nombres) {
        int ancho = prefijos.stream().mapToInt(String::length).max().orElse(0);
        List<String> textos = new java.util.ArrayList<>();
        for (int i = 0; i < prefijos.size(); i++) {
            String prefijo = prefijos.get(i);
            String nombre = nombres.get(i);
            textos.add(nombre == null || nombre.isBlank() ? prefijo
                    : prefijo + String.valueOf(ESPACIO_DURO).repeat(ancho - prefijo.length())
                            + ArticuloEmbalaje.SEPARADOR_NOMBRE.replace(' ', ESPACIO_DURO) + nombre);
        }
        return textos;
    }
}

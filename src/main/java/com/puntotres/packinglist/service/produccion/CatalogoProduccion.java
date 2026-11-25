package com.puntotres.packinglist.service.produccion;

import java.util.List;
import java.util.Map;

/**
 * Lo que el formulario de Hojas de Producción necesita del catálogo de un
 * cliente (o de una de sus temporadas): los artículos del desplegable y, por
 * código de modelo, las pieles enlazadas al bolso con sus colores, para los
 * desplegables de piel base y de combinación de cada línea.
 */
public record CatalogoProduccion(List<ArticuloProduccion> articulos, Map<String, PielesModelo> modelos) {

    public CatalogoProduccion {
        articulos = List.copyOf(articulos);
        modelos = Map.copyOf(modelos);
    }

    /**
     * Las pieles de un modelo. {@code pieles}: la piel de cada artículo del
     * modelo (AL0103, AL0216…), con los colores que lleva en cada color del
     * bolso. {@code combinaciones}: las pieles de combinación de esos
     * artículos, con sus colores.
     */
    public record PielesModelo(List<PielConColores> pieles, List<PielConColores> combinaciones) {
        public PielesModelo {
            pieles = List.copyOf(pieles);
            combinaciones = List.copyOf(combinaciones);
        }
    }

    /** Una piel por su nombre (o su referencia, si nadie se lo ha puesto) y sus colores, sin repetir. */
    public record PielConColores(String nombre, List<String> colores) {
        public PielConColores {
            colores = List.copyOf(colores);
        }
    }
}

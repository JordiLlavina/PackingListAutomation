package com.puntotres.packinglist.web;

import java.util.List;

import com.puntotres.packinglist.persistence.ResumenFoto;

/**
 * Un modelo del catálogo tal como lo pinta la pantalla: sus datos propios una
 * vez (van con rowspan sobre sus artículos) y una fila por artículo, cada una
 * con sus colores. Las cuatro casillas de combinación y de color de
 * combinación van siempre, vacías incluidas, para que la tabla tenga las
 * mismas columnas en todas las filas.
 */
public record FilaCatalogoVista(String codigoModelo, String nombre, String tipo, List<String> temporadas,
                                List<ResumenFoto> fotos, Long fotoPrincipalId, List<ArticuloVista> articulos) {

    public FilaCatalogoVista {
        temporadas = List.copyOf(temporadas);
        fotos = List.copyOf(fotos);
        articulos = List.copyOf(articulos);
    }

    public int numeroFotos() {
        return fotos.size();
    }

    public record ArticuloVista(String referencia, String refPiel, String nombrePiel, String forro,
                                List<String> combinaciones, String medidaCaja, boolean medidaConTara,
                                Integer unidadesPorCaja, Double pesoNetoKg, List<ColorVista> colores) {

        public ArticuloVista {
            combinaciones = List.copyOf(combinaciones);
            colores = List.copyOf(colores);
        }
    }

    public record ColorVista(String codigoColor, String descripcion, String colorPiel, String colorForro,
                             List<String> coloresCombinacion) {

        public ColorVista {
            coloresCombinacion = List.copyOf(coloresCombinacion);
        }
    }
}

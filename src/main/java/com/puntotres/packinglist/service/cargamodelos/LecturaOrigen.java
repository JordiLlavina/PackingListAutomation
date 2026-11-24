package com.puntotres.packinglist.service.cargamodelos;

import java.util.List;
import java.util.Map;

import com.puntotres.packinglist.service.corte.Imagen;

/**
 * Lo que un excel dice de los modelos, ya en el vocabulario del catálogo: una
 * entrada por referencia entera (modelo + piel) con sus colores. Los textos
 * de pieles y colores vienen en mayúsculas, como se guardan.
 *
 * @param croquis código de modelo → sus imágenes del excel (solo el de SMS las trae)
 */
public record LecturaOrigen(List<ArticuloLeido> articulos, Map<String, List<Imagen>> croquis, List<String> avisos) {

    public LecturaOrigen {
        articulos = List.copyOf(articulos);
        croquis = Map.copyOf(croquis);
        avisos = List.copyOf(avisos);
    }

    /**
     * @param referencia   la referencia entera, tal como la escribe el cliente
     * @param modelo       el código del modelo (la referencia si la regla del cliente no la parte)
     * @param refPiel      la referencia de la piel, o vacío
     * @param nombreModelo el nombre del bolso, o vacío si el excel no lo trae
     */
    public record ArticuloLeido(String referencia, String modelo, String refPiel, String nombreModelo,
                                String nombrePiel, String forro, List<String> combinaciones,
                                List<ColorLeido> colores) {

        public ArticuloLeido {
            combinaciones = List.copyOf(combinaciones);
            colores = List.copyOf(colores);
        }
    }

    /**
     * Un color del bolso en un artículo. Sin código (un bicolor de AMI, un
     * genérico) se identifica por su descripción, como en el catálogo.
     */
    public record ColorLeido(String codigo, String descripcion, String colorPiel, String colorForro,
                             List<String> coloresCombinacion) {

        public ColorLeido {
            coloresCombinacion = List.copyOf(coloresCombinacion);
        }

        /** La clave de {@code articulo_color}: el código o, si no tiene, la descripción entera en mayúsculas. */
        public String clave() {
            return codigo.isEmpty() ? descripcion.toUpperCase(java.util.Locale.ROOT) : codigo;
        }
    }
}

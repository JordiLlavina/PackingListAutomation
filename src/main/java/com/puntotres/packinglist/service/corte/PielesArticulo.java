package com.puntotres.packinglist.service.corte;

import java.util.List;
import java.util.Objects;

/**
 * Los nombres de las pieles de un artículo tal como se teclean en la pantalla
 * de pieles: la principal, el forro (solo si es de piel; vacío = no lleva) y
 * de cero a cuatro de combinación. Una combinación vacía no existe.
 */
public record PielesArticulo(String nombrePiel, String forro, List<String> combinaciones) {

    public PielesArticulo {
        nombrePiel = limpio(nombrePiel);
        forro = limpio(forro);
        combinaciones = combinaciones == null ? List.of() : combinaciones.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(combinacion -> !combinacion.isEmpty())
                .toList();
    }

    public boolean tieneForro() {
        return !forro.isEmpty();
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }
}

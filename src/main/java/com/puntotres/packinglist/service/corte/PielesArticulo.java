package com.puntotres.packinglist.service.corte;

import java.util.List;
import java.util.Objects;

/**
 * Las pieles de una orden de corte tal como se teclean en la pantalla de
 * pieles, cada una con su color: la principal, el forro (solo si es de piel;
 * sin nombre = no lleva) y de cero a cuatro de combinación. Una combinación
 * sin nombre no existe, aunque se le haya puesto color.
 */
public record PielesArticulo(PielCorte principal, PielCorte forro, List<PielCorte> combinaciones) {

    public PielesArticulo {
        principal = principal == null ? PielCorte.NINGUNA : principal;
        forro = forro == null ? PielCorte.NINGUNA : forro;
        combinaciones = combinaciones == null ? List.of() : combinaciones.stream()
                .filter(Objects::nonNull)
                .filter(PielCorte::existe)
                .toList();
    }

    /** Solo los nombres, sin colores. */
    public PielesArticulo(String nombrePiel, String forro, List<String> combinaciones) {
        this(new PielCorte(nombrePiel, ""), new PielCorte(forro, ""), combinaciones == null ? List.of()
                : combinaciones.stream().map(nombre -> new PielCorte(nombre, "")).toList());
    }

    public boolean tieneForro() {
        return forro.existe();
    }
}

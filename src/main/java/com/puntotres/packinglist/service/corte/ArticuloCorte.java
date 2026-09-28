package com.puntotres.packinglist.service.corte;

import java.util.List;

/**
 * Una referencia del pedido (modelo + piel) con sus colores: una fila de la
 * pantalla de pieles. Los nombres de piel, combinaciones y forro son los
 * mismos para todos sus colores, así que se teclean una vez por artículo, y
 * de ella sale una orden de corte por color.
 */
public record ArticuloCorte(ReferenciaCorte referencia, List<ColorCorte> colores) {

    public ArticuloCorte {
        colores = List.copyOf(colores);
    }
}

package com.puntotres.packinglist.service.taller;

import java.util.List;

import com.puntotres.packinglist.model.MedidaCaja;

/**
 * Una caja física ya llena, antes de tener número.
 *
 * Puede llevar varios artículos dentro (bulto mixto) y todos comparten el
 * mismo cartón: la medida es de la caja, no de lo que hay dentro.
 */
public record CajaGenerada(String destino, String medidaCaja, List<ContenidoCaja> contenido) {

    public CajaGenerada {
        contenido = List.copyOf(contenido);
    }

    public int unidades() {
        return contenido.stream().mapToInt(ContenidoCaja::unidades).sum();
    }

    /**
     * La altura del cartón, que es la ÚLTIMA dimensión de la medida y lo que
     * decide cuántas cajas caben en una pila. Cero si la medida no se puede
     * leer, y entonces el apilador lo convierte en bloqueo en vez de colocarla
     * a ciegas.
     */
    public int alturaCm() {
        return MedidaCaja.parse(medidaCaja).map(MedidaCaja::alto).orElse(0);
    }

    /** Un bulto mixto lleva más de un artículo dentro. */
    public boolean esMixta() {
        return contenido.size() > 1;
    }
}

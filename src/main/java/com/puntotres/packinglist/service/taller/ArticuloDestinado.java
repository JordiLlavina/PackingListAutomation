package com.puntotres.packinglist.service.taller;

/**
 * Un artículo con destinación y cantidad ya decididas: lo que de verdad se va
 * a empaquetar para un sitio concreto.
 *
 * Es la salida del reparto y la entrada de la agrupación en cajas. Lleva
 * encima su cartón y sus unidades por caja porque a partir de aquí nadie
 * vuelve a mirar la memoria ni la pantalla.
 */
public record ArticuloDestinado(
        String destino,
        String referencia,
        String color,
        String talla,
        String pedido,
        int cantidad,
        String medidaCaja,
        int unidadesPorCaja) {

    public String descripcion() {
        return referencia + " " + color + ("U".equals(talla) ? "" : " talla " + talla);
    }
}

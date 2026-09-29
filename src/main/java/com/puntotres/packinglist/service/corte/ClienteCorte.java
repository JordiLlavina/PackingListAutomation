package com.puntotres.packinglist.service.corte;

import java.io.IOException;

/**
 * Lo que cambia de un cliente a otro en los documentos del corte: cómo se
 * lee su excel de pedido y dónde lleva la piel su referencia. Se despacha por
 * clave de cliente, como las etiquetas; un cliente sin implementación sale
 * "en desarrollo" en la pantalla.
 */
public interface ClienteCorte {

    /** Clave del cliente en {@code packing-list.clientes} (AMI, APC). */
    String clave();

    /**
     * Lanza IllegalArgumentException con un mensaje en español si el fichero
     * no es el pedido de este cliente.
     */
    PedidoCorte leerPedido(byte[] excel) throws IOException;

    /**
     * Parte una referencia en modelo y piel con la regla del cliente. La usa
     * también la lectura de las fotos: una carpeta "ULL027.AL103" es el modelo
     * ULL027.
     */
    ReferenciaCorte partir(String referencia);
}

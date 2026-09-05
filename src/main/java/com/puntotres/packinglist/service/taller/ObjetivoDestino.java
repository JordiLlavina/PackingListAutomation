package com.puntotres.packinglist.service.taller;

/**
 * Cuántas unidades de un artículo pide el cliente para una destinación, y con
 * qué número de pedido.
 *
 * Es el OBJETIVO, no lo que ha llegado del taller: la cantidad que se envía
 * sale de aquí y no de la hoja del taller, que solo dice lo que se ha
 * fabricado. Cuando no llega para todo, el reparto decide quién se queda
 * corto.
 */
public record ObjetivoDestino(String destino, int cantidad, String pedido) {
}

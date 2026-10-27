package com.puntotres.packinglist.service.corte;

/**
 * De dónde salen las referencias y las cantidades de los documentos del corte.
 *
 * PRODUCCION: el excel de producción de Puntotres, el mismo para todos los
 * clientes. Trae la cantidad que se corta (la del taller), la piel y su
 * color, así que no se consultan ni se guardan la memoria de pieles ni la
 * Tabla colores Clientes. Es la de por defecto. Los documentos que salen
 * son los mismos con los dos: con fotos, también el Word de fotos.
 *
 * PEDIDO: el excel de pedido del cliente (solo AMI y APC), con la memoria de
 * pieles y la Tabla colores Clientes, como siempre.
 */
public enum OrigenCorte {
    PRODUCCION,
    PEDIDO
}

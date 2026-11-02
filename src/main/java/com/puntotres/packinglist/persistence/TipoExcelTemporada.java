package com.puntotres.packinglist.persistence;

/**
 * Qué excel guarda una temporada. Los clientes con {@code pedido-cliente}
 * (AMI, APC) guardan el excel de PEDIDO del cliente, que el packing list, las
 * etiquetas y el corte leen como pedido; un cliente genérico guarda el excel
 * de PRODUCCION de Puntotres, que es de donde salen sus modelos y que solo
 * saben leer el corte y el catálogo. Los desplegables que leen el excel como
 * pedido no deben ofrecer los de producción.
 */
public enum TipoExcelTemporada {
    PEDIDO, PRODUCCION
}

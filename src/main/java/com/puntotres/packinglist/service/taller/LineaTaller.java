package com.puntotres.packinglist.service.taller;

/**
 * Una fila de la hoja "LISTE DE COLIS" del taller.
 *
 * De aquí solo se aprovecha la información de ARTÍCULO. El packing del
 * taller —su numeración de cajas y su reparto en bultos— no se respeta: el
 * programa lo regenera desde cero con las normas del cliente, así que
 * {@code numeroColis} no se guarda siquiera.
 *
 * {@code unidadesPorCajaTaller} es una SUGERENCIA, y puede ser null: muchos
 * talleres no traen esa columna. Null no significa cero, significa que hay
 * que resolverlo por otro lado (memoria del programa o el usuario).
 *
 * {@code fila} es el número de fila tal como lo ve el usuario en Excel
 * (empezando en 1), para poder nombrarla en un aviso.
 */
public record LineaTaller(
        int fila,
        String cliente,
        String motivo,
        String referencia,
        String color,
        String talla,
        String destinoTaller,
        String code,
        String numeroExpedicion,
        Integer unidadesPorCajaTaller,
        int cantidad) {
}

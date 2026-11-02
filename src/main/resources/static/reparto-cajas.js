/*
 * Reparto en cajas de la hoja de «Info de embalaje para taller», calculado a
 * partir de la tabla de destinaciones y las unidades por caja, como lo
 * escriben las hojas de los talleres:
 *
 *   4 CAJAS X 10 BOLSOS: 40 - JAPAN
 *   12 CAJAS X 10 BOLSOS: 120 - RETAIL
 *   1 CAJA X 7 BOLSOS: 7 - RETAIL
 *
 * Regla simple a propósito: por cada destinación, las cajas llenas y una caja
 * con lo que sobra. Ni reparte equitativo ni mezcla destinaciones en una caja
 * (eso lo hace el taller a veces, y entonces se corrige a mano en el campo).
 *
 * Es una función pura, sin tocar la página, para poder probarla sola.
 */
(function (global) {
    'use strict';

    function plural(numero, singular, plural) {
        return numero + ' ' + (numero === 1 ? singular : plural);
    }

    /** Un entero positivo o null: «10» vale, «», «0», «-3» o «abc» no. */
    function enteroPositivo(valor) {
        const numero = typeof valor === 'number' ? valor : parseInt(String(valor == null ? '' : valor).trim(), 10);
        return Number.isInteger(numero) && numero > 0 ? numero : null;
    }

    /**
     * El texto del reparto, una línea por caja tipo, en el orden de la tabla.
     * filas: [{destino, cantidad}] tal como están escritos en la tabla.
     * Devuelve '' si no se saben las unidades por caja o no hay nada que repartir.
     */
    function textoReparto(filas, unidadesPorCaja) {
        const porCaja = enteroPositivo(unidadesPorCaja);
        if (porCaja === null) {
            return '';
        }
        const lineas = [];
        (filas || []).forEach(function (fila) {
            const destino = String(fila && fila.destino != null ? fila.destino : '').trim().toUpperCase();
            const cantidad = enteroPositivo(fila && fila.cantidad);
            if (!destino || cantidad === null) {
                return;
            }
            const llenas = Math.floor(cantidad / porCaja);
            const resto = cantidad % porCaja;
            if (llenas > 0) {
                lineas.push(plural(llenas, 'CAJA', 'CAJAS') + ' X ' + plural(porCaja, 'BOLSO', 'BOLSOS')
                        + ': ' + (llenas * porCaja) + ' - ' + destino);
            }
            if (resto > 0) {
                lineas.push('1 CAJA X ' + plural(resto, 'BOLSO', 'BOLSOS') + ': ' + resto + ' - ' + destino);
            }
        });
        return lineas.join('\n');
    }

    global.textoReparto = textoReparto;
})(window);

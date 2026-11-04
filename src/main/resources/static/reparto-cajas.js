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
 * con lo que sobra. Ni reparte equitativo (eso se corrige a mano en el campo)
 * ni mezcla destinaciones en una caja, que desde que el reparto va en la
 * tabla de destinaciones de la hoja ya no se admite.
 *
 * Ese es además el único formato aceptado: comprobarReparto, más abajo,
 * revisa lo escrito con las mismas reglas que RepartoCajas.java.
 *
 * Son funciones puras, sin tocar la página, para poder probarlas solas.
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

    /*
     * La comprobación del reparto mientras se teclea. Repite las reglas y los
     * mensajes de RepartoCajas.java, que es la autoridad: el servidor rechaza
     * la hoja con los mismos errores aunque aquí no se hubieran visto.
     */
    const LINEA = /^\s*(\d{1,6})\s+CAJAS?\s+X\s+(\d{1,6})\s+BOLSOS?\s*:\s*(\d{1,6})\s*-\s*(\S.*?)\s*$/i;
    const FORMATO = 'N CAJAS X M BOLSOS: TOTAL - DESTINACIÓN';

    /** Recortado, con los espacios de dentro reducidos a uno y en mayúsculas. */
    function normalizar(destino) {
        return String(destino == null ? '' : destino).trim().replace(/\s+/g, ' ').toUpperCase();
    }

    /**
     * Errores (bloquean la generación) y avisos (no) del reparto, cada uno
     * con su número de línea contando también las líneas en blanco.
     * filas: [{destino, cantidad}] tal como están escritos en la tabla.
     * Devuelve {errores: [...], avisos: [...]}; un texto vacío no da ninguno.
     */
    function comprobarReparto(texto, filas) {
        const tabla = (filas || []).map(function (fila) {
            const cantidad = parseInt(String(fila && fila.cantidad != null ? fila.cantidad : '').trim(), 10);
            return {
                destino: String(fila && fila.destino != null ? fila.destino : '').trim(),
                nombre: normalizar(fila && fila.destino),
                cantidad: isNaN(cantidad) ? 0 : cantidad,
                suma: 0
            };
        });
        // El servidor descarta las filas que no llevan nada escrito, así que
        // una fila sin destinación no puede recibir líneas.
        const nombres = tabla.map(function (fila) { return fila.nombre; });
        const errores = [];
        const avisos = [];
        let algunaBuena = false;
        const lineas = String(texto == null ? '' : texto).trim() === '' ? [] : String(texto).split(/\r\n|\r|\n/);
        lineas.forEach(function (bruta, i) {
            const linea = bruta.replace(/ /g, ' ');
            if (linea.trim() === '') {
                return;
            }
            const prefijo = 'Línea ' + (i + 1) + ': ';
            const partes = LINEA.exec(linea);
            if (!partes) {
                errores.push(prefijo + '«' + linea.trim() + '» no tiene el formato ' + FORMATO);
                return;
            }
            const cajas = parseInt(partes[1], 10);
            const porCaja = parseInt(partes[2], 10);
            const total = parseInt(partes[3], 10);
            const destino = partes[4].trim().replace(/\s+/g, ' ');
            const fila = destino ? nombres.indexOf(normalizar(destino)) : -1;
            let buena = true;
            if (cajas * porCaja !== total) {
                errores.push(prefijo + (cajas === 1 ? '1 caja de ' + porCaja + ' es '
                        : cajas + ' cajas de ' + porCaja + ' son ') + (cajas * porCaja) + ', no ' + total);
                buena = false;
            }
            if (fila < 0) {
                errores.push(prefijo + destino + ' no está en la tabla de destinaciones');
                buena = false;
            }
            if (buena) {
                tabla[fila].suma += total;
                algunaBuena = true;
            }
        });
        if (algunaBuena) {
            tabla.forEach(function (fila, i) {
                if (!fila.nombre || nombres.indexOf(fila.nombre) !== i) {
                    return;
                }
                if (fila.suma !== fila.cantidad) {
                    avisos.push(fila.destino + ': las cajas suman ' + fila.suma + ' y la cantidad es ' + fila.cantidad);
                }
            });
        }
        return {errores: errores, avisos: avisos};
    }

    global.textoReparto = textoReparto;
    global.comprobarReparto = comprobarReparto;
})(window);

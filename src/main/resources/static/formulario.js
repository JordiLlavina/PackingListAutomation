/* Modo FORMULARIO de la pantalla de entrada: construye dinámicamente el
   formulario de destinaciones / palets / referencias / cajas y, al enviar,
   lo serializa al campo "json" con el mismo formato EnvioInput que el modo
   JSON — el servidor no distingue entre ambos modos.

   Este script debe cargarse ANTES del script inline de entrada.html: su
   listener de submit tiene que ejecutarse primero para poder cancelar el
   envío (stopImmediatePropagation) sin dejar el botón deshabilitado. */
(function () {
    'use strict';

    const lista = document.getElementById('listaDestinos');
    const campoJson = document.getElementById('json');
    const campoModo = document.getElementById('modo');
    const errorFormulario = document.getElementById('errorFormulario');

    function clonar(idPlantilla) {
        return document.getElementById(idPlantilla).content.firstElementChild.cloneNode(true);
    }

    function campo(ambito, nombre) {
        return ambito.querySelector('[data-campo="' + nombre + '"]');
    }

    /** Valor entero de un campo, o null si está vacío o no es un número. */
    function entero(ambito, nombre) {
        const valor = parseInt(campo(ambito, nombre).value, 10);
        return isNaN(valor) ? null : valor;
    }

    // --- Alta de bloques ---

    function anadirDestino() {
        const destino = clonar('plantillaDestino');
        lista.appendChild(destino);
        anadirReferencia(destino);
        return destino;
    }

    function anadirPalet(destino) {
        const fila = clonar('plantillaPalet');
        const contenedor = destino.querySelector('[data-lista="palets"]');
        campo(fila, 'palet').value = contenedor.children.length + 1;
        contenedor.appendChild(fila);
        return fila;
    }

    function anadirReferencia(destino) {
        const referencia = clonar('plantillaReferencia');
        destino.querySelector('[data-lista="referencias"]').appendChild(referencia);
        anadirCaja(referencia);
        return referencia;
    }

    function anadirCaja(referencia) {
        const fila = clonar('plantillaCaja');
        // La numeración de cajas continúa donde acabó la última fila de la
        // destinación (las cajas se numeran por destinación, no por referencia).
        const siguiente = 1 + maxNumeroCaja(referencia.closest('.f-destino'));
        if (siguiente > 1) {
            campo(fila, 'cajaInicio').value = siguiente;
        }
        referencia.querySelector('[data-lista="cajas"]').appendChild(fila);
        return fila;
    }

    function maxNumeroCaja(destino) {
        let max = 0;
        destino.querySelectorAll('.f-caja').forEach(function (fila) {
            max = Math.max(max, entero(fila, 'cajaFin') || entero(fila, 'cajaInicio') || 0);
        });
        return max;
    }

    /** Misma referencia en otro color: copia todo menos el color y las cajas. */
    function duplicarReferencia(referencia) {
        const nueva = clonar('plantillaReferencia');
        ['referencia', 'medidaCaja', 'pedido', 'talla', 'modelo', 'canal', 'livraisonCode']
            .forEach(function (nombre) {
                campo(nueva, nombre).value = campo(referencia, nombre).value;
            });
        referencia.parentNode.insertBefore(nueva, referencia.nextElementSibling);
        anadirCaja(nueva);
        campo(nueva, 'color').focus();
        return nueva;
    }

    // --- Total en vivo por referencia (contraste con el papel) ---

    function actualizarTotal(referencia) {
        let unidades = 0;
        let cajas = 0;
        referencia.querySelectorAll('.f-caja').forEach(function (fila) {
            const inicio = entero(fila, 'cajaInicio');
            if (inicio == null) {
                return;
            }
            const numero = ((entero(fila, 'cajaFin') || inicio) - inicio) + 1;
            if (numero < 1) {
                return;
            }
            cajas += numero;
            unidades += numero * (entero(fila, 'unidadesPorCaja') || 0);
        });
        const total = referencia.querySelector('[data-total]');
        total.textContent = unidades + ' uds en ' + cajas + ' caja' + (cajas === 1 ? '' : 's');
        const declarado = entero(referencia, 'cantidadTotal');
        total.classList.toggle('descuadre', declarado != null && declarado !== unidades);
    }

    // --- Serialización a EnvioInput ---

    function serializar() {
        const errores = [];
        const destinos = [];
        lista.querySelectorAll('.f-destino').forEach(function (tarjeta, indice) {
            const nombre = campo(tarjeta, 'destino').value.trim();
            const etiqueta = nombre || 'destinación ' + (indice + 1);
            const palets = serializarPalets(tarjeta, etiqueta, errores);
            const referencias = serializarReferencias(tarjeta, etiqueta, errores);
            if (!nombre && palets.length === 0 && referencias.length === 0) {
                return; // tarjeta completamente vacía: se ignora
            }
            if (!nombre) {
                errores.push('Hay una destinación sin nombre');
            }
            if (referencias.length === 0) {
                errores.push(etiqueta + ': añade al menos una referencia con sus cajas');
            }
            const destino = { destino: nombre, referencias: referencias };
            if (palets.length > 0) {
                destino.palets = palets;
            }
            destinos.push(destino);
        });
        if (destinos.length === 0) {
            errores.push('Añade al menos una destinación con sus referencias y cajas');
        }
        const cliente = document.getElementById('cliente').value;
        return { errores: errores, envio: { cliente: cliente || null, destinos: destinos } };
    }

    function serializarPalets(tarjeta, etiqueta, errores) {
        const palets = [];
        tarjeta.querySelectorAll('.f-palet').forEach(function (fila) {
            const numero = entero(fila, 'palet');
            const inicio = entero(fila, 'cajaInicio');
            const fin = entero(fila, 'cajaFin');
            const medidas = campo(fila, 'medidas').value.trim();
            const tara = parseFloat(campo(fila, 'tara').value);
            if (numero == null && inicio == null && fin == null && !medidas && isNaN(tara)) {
                return; // fila vacía: se ignora
            }
            if (numero == null || inicio == null || fin == null) {
                errores.push(etiqueta + ': cada palet necesita nº y rango de cajas (desde y hasta)');
                return;
            }
            const palet = { palet: numero, cajaInicio: inicio, cajaFin: fin };
            if (medidas) {
                palet.medidas = medidas;
            }
            if (!isNaN(tara)) {
                palet.tara = tara;
            }
            palets.push(palet);
        });
        return palets;
    }

    function serializarReferencias(tarjeta, etiqueta, errores) {
        const referencias = [];
        tarjeta.querySelectorAll('.f-referencia').forEach(function (bloque) {
            const codigo = campo(bloque, 'referencia').value.trim();
            const cajas = serializarCajas(bloque, etiqueta + (codigo ? ' / ' + codigo : ''), errores);
            const vacia = !codigo && cajas.length === 0;
            if (vacia) {
                return;
            }
            if (!codigo) {
                errores.push(etiqueta + ': hay una referencia sin código');
            }
            if (cajas.length === 0) {
                errores.push(etiqueta + ' / ' + codigo + ': la referencia no tiene ninguna fila de cajas');
            }
            const referencia = { referencia: codigo, cajas: cajas };
            ['color', 'medidaCaja', 'pedido', 'talla', 'modelo', 'canal', 'livraisonCode']
                .forEach(function (nombre) {
                    const valor = campo(bloque, nombre).value.trim();
                    if (valor) {
                        referencia[nombre] = valor;
                    }
                });
            const declarado = entero(bloque, 'cantidadTotal');
            if (declarado != null) {
                referencia.cantidadTotal = declarado;
            }
            referencias.push(referencia);
        });
        return referencias;
    }

    function serializarCajas(bloque, etiqueta, errores) {
        const cajas = [];
        bloque.querySelectorAll('.f-caja').forEach(function (fila) {
            const inicio = entero(fila, 'cajaInicio');
            const fin = entero(fila, 'cajaFin');
            const unidades = entero(fila, 'unidadesPorCaja');
            if (inicio == null && fin == null && unidades == null) {
                return; // fila vacía: se ignora
            }
            if (inicio == null || unidades == null) {
                errores.push(etiqueta + ': cada fila de cajas necesita "caja desde" y "uds por caja"');
                return;
            }
            const hasta = fin == null ? inicio : fin;
            if (hasta < inicio) {
                errores.push(etiqueta + ': el rango de cajas ' + inicio + '-' + hasta + ' está invertido');
                return;
            }
            // Siempre en forma de rango: un rango de una sola caja
            // (inicio == fin) equivale a la caja suelta del JSON.
            cajas.push({ cajaInicio: inicio, cajaFin: hasta, unidadesPorCaja: unidades });
        });
        return cajas;
    }

    // --- Recarga desde el JSON (al volver con errores de validación) ---

    function cargar(envio) {
        (envio.destinos || []).forEach(function (destinoJson) {
            const destino = anadirDestino();
            destino.querySelector('.f-referencia').remove(); // la semilla vacía
            campo(destino, 'destino').value = destinoJson.destino || '';
            (destinoJson.palets || []).forEach(function (paletJson) {
                const fila = anadirPalet(destino);
                campo(fila, 'palet').value = valorO(paletJson.palet);
                campo(fila, 'cajaInicio').value = valorO(paletJson.cajaInicio);
                campo(fila, 'cajaFin').value = valorO(paletJson.cajaFin);
                campo(fila, 'medidas').value = valorO(paletJson.medidas);
                campo(fila, 'tara').value = valorO(paletJson.tara);
            });
            (destinoJson.referencias || []).forEach(function (referenciaJson) {
                const referencia = anadirReferencia(destino);
                referencia.querySelector('.f-caja').remove(); // la semilla vacía
                ['referencia', 'color', 'medidaCaja', 'pedido', 'talla', 'modelo',
                    'canal', 'livraisonCode', 'cantidadTotal'].forEach(function (nombre) {
                        campo(referencia, nombre).value = valorO(referenciaJson[nombre]);
                    });
                (referenciaJson.cajas || []).forEach(function (cajaJson) {
                    const fila = anadirCaja(referencia);
                    campo(fila, 'cajaInicio').value =
                        valorO(cajaJson.cajaInicio != null ? cajaJson.cajaInicio : cajaJson.caja);
                    campo(fila, 'cajaFin').value = valorO(cajaJson.cajaFin);
                    campo(fila, 'unidadesPorCaja').value =
                        valorO(cajaJson.unidadesPorCaja != null
                            ? cajaJson.unidadesPorCaja : cajaJson.unidades);
                });
                actualizarTotal(referencia);
            });
        });
    }

    function valorO(valor) {
        return valor == null ? '' : valor;
    }

    // --- Eventos (delegados en el contenedor de destinaciones) ---

    lista.addEventListener('click', function (evento) {
        const boton = evento.target.closest('button');
        if (!boton) {
            return;
        }
        if (boton.dataset.anadir === 'palet') {
            anadirPalet(boton.closest('.f-destino'));
        } else if (boton.dataset.anadir === 'referencia') {
            anadirReferencia(boton.closest('.f-destino'));
        } else if (boton.dataset.anadir === 'caja') {
            const referencia = boton.closest('.f-referencia');
            anadirCaja(referencia);
            actualizarTotal(referencia);
        } else if (boton.dataset.accion === 'duplicar') {
            duplicarReferencia(boton.closest('.f-referencia'));
        } else if (boton.dataset.eliminar === 'destino') {
            boton.closest('.f-destino').remove();
        } else if (boton.dataset.eliminar === 'referencia') {
            boton.closest('.f-referencia').remove();
        } else if (boton.dataset.eliminar === 'fila') {
            const referencia = boton.closest('.f-referencia');
            boton.closest('.f-fila').remove();
            if (referencia) {
                actualizarTotal(referencia);
            }
        }
    });

    lista.addEventListener('input', function (evento) {
        const referencia = evento.target.closest('.f-referencia');
        if (referencia) {
            actualizarTotal(referencia);
        }
    });

    document.getElementById('botonAnadirDestino').addEventListener('click', function () {
        anadirDestino();
    });

    document.getElementById('formEntrada').addEventListener('submit', function (evento) {
        if (campoModo.value !== 'FORMULARIO') {
            return;
        }
        const resultado = serializar();
        if (resultado.errores.length > 0) {
            evento.preventDefault();
            evento.stopImmediatePropagation(); // el listener de "Procesando..." no debe correr
            errorFormulario.textContent = resultado.errores.join('\n');
            errorFormulario.style.display = '';
            errorFormulario.scrollIntoView({ behavior: 'smooth', block: 'center' });
            return;
        }
        errorFormulario.style.display = 'none';
        campoJson.value = JSON.stringify(resultado.envio, null, 2);
    });

    // --- Estado inicial ---

    function asegurarSemilla() {
        if (!lista.querySelector('.f-destino')) {
            anadirDestino();
        }
    }

    document.getElementById('botonModoFORMULARIO').addEventListener('click', asegurarSemilla);

    // Al volver del servidor con errores de validación en modo FORMULARIO,
    // el textarea del JSON conserva lo serializado: se reconstruye el
    // formulario desde ahí para no perder lo tecleado.
    if (campoModo.value === 'FORMULARIO') {
        if (campoJson.value.trim()) {
            try {
                cargar(JSON.parse(campoJson.value));
            } catch (error) {
                // JSON irrecuperable: se empieza con el formulario vacío.
            }
        }
        asegurarSemilla();
    }
})();

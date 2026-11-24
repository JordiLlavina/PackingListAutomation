// Carga de modelos en batería (/carga-modelos/bateria).
//
// El lote vive en la sesión del servidor y esta página solo lo pinta: lo pide
// entero a /carga-modelos/estado y manda cada cambio por fetch. Por eso
// recargar no pierde nada, y por eso aquí no se decide nada: qué le falta a
// un modelo, si ya existe o qué trae de nuevo lo dice siempre el servidor.
//
// Tres cosas que no se ven a primera vista:
//  - Las peticiones van EN COLA, de una en una y en orden: teclear el nombre
//    y pulsar «Aceptar Modelo» son dos peticiones seguidas, y la segunda
//    tiene que encontrar ya aplicada la primera.
//  - Un campo se manda al salir de él (change), con el panel ENTERO: el
//    servidor compara con lo que tenía y apunta qué se ha tocado.
//  - Tras cada respuesta el panel se vuelve a pintar y el foco vuelve al
//    campo donde estaba (cada campo lleva su clave en data-k).
(function () {
    const raiz = document.getElementById('bateria');
    const BASE = raiz.dataset.base;
    const FOTO_CATALOGO = raiz.dataset.fotoCatalogo;
    const CATALOGO = raiz.dataset.catalogo;
    const lista = document.getElementById('lista');
    const panel = document.getElementById('panel');
    const errorGeneral = document.getElementById('errorGeneral');
    const MAX_COMBINACIONES = 4;

    let lote = null;
    let elegido = null;
    let filtro = '';
    let cola = Promise.resolve();
    let sondeo = null;
    let caducada = false;
    // Lo que se recuerda de cada modelo mientras se revisa: qué piel está abierta y sus avisos.
    const pielAbierta = {};
    const avisosDeModelo = {};

    // --- Utilidades ---

    /** Crea un elemento: h('button', {class: 'x', onclick: f, hidden: true}, 'texto', otroNodo). */
    function h(etiqueta, propiedades) {
        const nodo = document.createElementNS(
            SVG.indexOf(etiqueta) >= 0 ? 'http://www.w3.org/2000/svg' : 'http://www.w3.org/1999/xhtml', etiqueta);
        Object.keys(propiedades || {}).forEach(function (clave) {
            const valor = propiedades[clave];
            if (valor === null || valor === undefined || valor === false) { return; }
            if (clave.indexOf('on') === 0 && typeof valor === 'function') {
                nodo.addEventListener(clave.substring(2), valor);
            } else if (clave === 'value') {
                nodo.value = valor;
            } else {
                nodo.setAttribute(clave, valor === true ? '' : valor);
            }
        });
        for (let i = 2; i < arguments.length; i++) {
            anadir(nodo, arguments[i]);
        }
        return nodo;
    }
    const SVG = ['svg', 'path', 'circle', 'rect'];

    function anadir(nodo, hijo) {
        if (hijo === null || hijo === undefined || hijo === false) { return; }
        if (Array.isArray(hijo)) { hijo.forEach(function (uno) { anadir(nodo, uno); }); return; }
        nodo.appendChild(typeof hijo === 'string' || typeof hijo === 'number'
            ? document.createTextNode(String(hijo)) : hijo);
    }

    function icono(trazos, clase) {
        return h('svg', { class: clase || 'icono', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor',
            'stroke-width': '1.8', 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true' },
            trazos.map(function (trazo) { return h('path', { d: trazo }); }));
    }
    const ICONOS = {
        aceptado: ['M5 12.5 10 17.5 19 7.5'],
        cancelado: ['M6.5 6.5 17.5 17.5', 'M17.5 6.5 6.5 17.5'],
        sinFotos: ['M4 7.5h3l1.5-2h7l1.5 2h3v11H4z', 'M9.2 13a2.8 2.8 0 1 0 5.6 0 2.8 2.8 0 0 0-5.6 0', 'M3 3 21 21'],
        pdf: ['M6.5 3h7.2L19 8.3v11.2a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19.5v-15A1.5 1.5 0 0 1 6.5 3z',
            'M13.5 3v5.5H19', 'M8.5 13h7M8.5 16.5h5'],
        mas: ['M12 5v14', 'M5 12h14']
    };

    function plural(numero, uno, varios) {
        return numero + ' ' + (numero === 1 ? uno : varios);
    }

    function mostrarError(mensaje) {
        errorGeneral.hidden = !mensaje;
        errorGeneral.textContent = mensaje || '';
        if (mensaje) { errorGeneral.scrollIntoView({ block: 'nearest' }); }
    }

    /**
     * Una petición a la batería, en cola. `cuerpo` es un objeto (va en JSON),
     * un FormData (ficheros) o nada. Un error se enseña arriba y rechaza.
     */
    function pedir(metodo, ruta, cuerpo) {
        const resultado = cola.then(function () {
            const opciones = { method: metodo, headers: { Accept: 'application/json' } };
            if (cuerpo instanceof FormData) {
                opciones.body = cuerpo;
            } else if (cuerpo !== undefined) {
                opciones.headers['Content-Type'] = 'application/json';
                opciones.body = JSON.stringify(cuerpo);
            }
            return fetch(BASE + ruta, opciones);
        }).then(function (respuesta) {
            if (respuesta.ok) {
                mostrarError('');
                return respuesta.status === 204 ? null : respuesta.json();
            }
            return respuesta.json().catch(function () { return {}; }).then(function (error) {
                if (respuesta.status === 410) { sesionCaducada(error.mensaje); }
                throw new Error(error.mensaje || 'No se ha podido hacer: vuelve a intentarlo.');
            });
        });
        cola = resultado.catch(function () { /* la cola sigue aunque una falle */ });
        return resultado.catch(function (error) {
            if (!caducada) { mostrarError(error.message); }
            throw error;
        });
    }

    function sesionCaducada(mensaje) {
        caducada = true;
        clearInterval(sondeo);
        errorGeneral.hidden = false;
        errorGeneral.textContent = '';
        anadir(errorGeneral, [mensaje || 'La carga ya no está. ',
            ' ', h('a', { href: BASE }, 'Volver a elegir el origen')]);
        panel.textContent = '';
        lista.textContent = '';
    }

    function modeloElegido() {
        return lote ? lote.modelos.find(function (modelo) { return modelo.id === elegido; }) || null : null;
    }

    function sustituir(modelo) {
        const indice = lote.modelos.findIndex(function (uno) { return uno.id === modelo.id; });
        if (indice >= 0) { lote.modelos[indice] = modelo; }
    }

    function clienteDe(modelo) {
        return lote.clientes[modelo.cliente] || { clave: modelo.cliente, nombre: modelo.cliente,
            parteReferencias: false, temporadas: [] };
    }

    // --- Lo que se le manda al servidor: el panel entero del modelo ---

    function edicion(modelo) {
        return {
            cliente: modelo.cliente,
            codigo: modelo.codigo,
            nombrePrototipo: modelo.nombrePrototipo,
            temporadaId: modelo.temporadaId,
            nombre: modelo.nombre,
            tipo: modelo.tipo,
            etapa: modelo.etapa,
            pieles: modelo.codigo ? modelo.pieles.map(function (piel) {
                return {
                    referencia: piel.referencia, nombrePiel: piel.nombrePiel, forro: piel.forro,
                    combinaciones: piel.combinaciones, medidaCaja: piel.medidaCaja,
                    unidadesPorCaja: piel.unidadesPorCaja, pesoNetoKg: piel.pesoNetoKg,
                    colores: piel.colores.map(function (color) {
                        return { codigo: color.codigo, descripcion: color.codigo ? color.descripcion : color.clave,
                            colorPiel: color.colorPiel, colorForro: color.colorForro,
                            coloresCombinacion: color.coloresCombinacion };
                    })
                };
            }) : null,
            fotos: modelo.fotos.map(function (foto) { return { id: foto.id, seleccionada: foto.seleccionada }; })
        };
    }

    function guardar(modelo) {
        return pedir('PUT', '/modelos/' + modelo.id, edicion(modelo)).then(function (respuesta) {
            aplicar(respuesta);
        }, function () { recargar(); });
    }

    /** La respuesta a un cambio en un modelo: el modelo nuevo y los recuentos. */
    function aplicar(respuesta) {
        sustituir(respuesta.modelo);
        lote.cuentas = respuesta.cuentas;
        lote.fotos = respuesta.fotos;
        avisosDeModelo[respuesta.modelo.id] = respuesta.avisos && respuesta.avisos.length ? respuesta.avisos : null;
        pintar();
    }

    function recargar() {
        return pedir('GET', '/estado').then(function (estado) {
            lote = estado;
            if (!modeloElegido()) { elegido = primeroPorRevisar(); }
            pintar();
            vigilarFotos();
        }, function () { /* ya se ha enseñado el error */ });
    }

    // --- Elegir el siguiente ---

    function primeroPorRevisar(desde) {
        if (!lote || !lote.modelos.length) { return null; }
        const modelos = lote.modelos;
        const inicio = Math.max(0, modelos.findIndex(function (modelo) { return modelo.id === desde; }));
        for (let i = 0; i < modelos.length; i++) {
            const modelo = modelos[(inicio + i) % modelos.length];
            if (modelo.estado === 'PENDIENTE' && !modelo.gris && modelo.id !== desde) { return modelo.id; }
        }
        return desde !== undefined && desde !== null ? desde : modelos[0].id;
    }

    function marcar(modelo, estado) {
        return pedir('POST', '/modelos/' + modelo.id + '/estado?estado=' + estado).then(function (estadoLote) {
            lote = estadoLote;
            // Aceptado o cancelado, ya está: se pasa al siguiente por completar.
            if (estado !== 'PENDIENTE') { elegido = primeroPorRevisar(modelo.id); }
            pintar();
            panel.scrollTop = 0;
        }, function () { /* ya se ha enseñado el error */ });
    }

    // --- La barra de arriba ---

    function pintarBarra() {
        const cuentas = lote.cuentas;
        const grises = lote.modelos.filter(function (modelo) {
            return modelo.gris && modelo.estado === 'PENDIENTE';
        }).length;
        const porRevisar = cuentas.pendientes - grises;
        const recuento = document.getElementById('recuento');
        recuento.textContent = '';
        anadir(recuento, [
            h('strong', { class: 'cuenta-aceptados' }, plural(cuentas.aceptados, 'aceptado', 'aceptados')),
            ', ' + porRevisar + ' por revisar',
            cuentas.cancelados ? ', ' + plural(cuentas.cancelados, 'cancelado', 'cancelados') : ''
        ]);
        const pendientes = document.getElementById('pendientes');
        const partes = [];
        if (porRevisar > 0) {
            partes.push(porRevisar === 1 ? 'Queda 1 modelo por aceptar o cancelar: no se cargará.'
                : 'Quedan ' + porRevisar + ' modelos por aceptar o cancelar: no se cargarán.');
        }
        if (grises > 0) {
            partes.push(plural(grises, 'ya existe y no trae', 'ya existen y no traen') + ' nada nuevo.');
        }
        pendientes.hidden = !partes.length;
        pendientes.textContent = partes.join(' ');

        const progreso = document.getElementById('progresoFotos');
        progreso.hidden = lote.fotos.terminadas;
        progreso.textContent = 'Preparando las fotos: ' + lote.fotos.hechas + ' de ' + lote.fotos.total + '…';

        const cargar = document.getElementById('botonCargar');
        cargar.textContent = 'Cargar modelos al Sistema' + (cuentas.aceptados ? ' (' + cuentas.aceptados + ')' : '');
        cargar.disabled = !cuentas.aceptados || !lote.fotos.terminadas;
        cargar.title = !lote.fotos.terminadas ? 'Espera a que acaben de prepararse las fotos'
            : (cuentas.aceptados ? 'Carga al catálogo los modelos que están en verde' : 'Acepta algún modelo antes');
        document.getElementById('botonAnadir').hidden = lote.origen !== 'MANUAL';
        const completos = lote.modelos.filter(function (modelo) {
            return modelo.estado === 'PENDIENTE' && !modelo.gris && !modelo.faltan.length;
        }).length;
        const aceptarTodos = document.getElementById('botonAceptarCompletos');
        aceptarTodos.hidden = completos < 2;
        aceptarTodos.textContent = 'Aceptar los ' + completos + ' completos';
    }

    function pintarAvisos() {
        const bloque = document.getElementById('avisosLote');
        bloque.hidden = !lote.avisos.length;
        bloque.querySelector('summary').textContent = plural(lote.avisos.length, 'aviso', 'avisos') + ' de la lectura';
        const ul = bloque.querySelector('ul');
        ul.textContent = '';
        lote.avisos.forEach(function (aviso) { ul.appendChild(h('li', {}, aviso)); });
    }

    // --- La lista de la izquierda ---

    function pintarLista() {
        lista.textContent = '';
        const texto = filtro.trim().toUpperCase();
        let separado = false;
        let pintados = 0;
        lote.modelos.forEach(function (modelo) {
            if (texto && (modelo.visible + ' ' + modelo.nombre).toUpperCase().indexOf(texto) < 0) { return; }
            if (modelo.gris && !separado) {
                separado = true;
                lista.appendChild(h('li', { class: 'bateria-separador' }, 'Ya existen, sin nada nuevo'));
            }
            pintados++;
            const falta = modelo.estado === 'PENDIENTE' && modelo.faltan.length;
            lista.appendChild(h('li', {},
                h('button', {
                    type: 'button', class: 'bateria-modelo' + (modelo.id === elegido ? ' elegido' : ''),
                    'data-estado': modelo.estado, 'data-gris': modelo.gris ? 'si' : null,
                    'aria-current': modelo.id === elegido ? 'true' : null,
                    onclick: function () { elegido = modelo.id; pintar(); panel.scrollTop = 0; }
                },
                    h('span', { class: 'bateria-marca' },
                        modelo.estado === 'ACEPTADO' ? icono(ICONOS.aceptado)
                            : (modelo.estado === 'CANCELADO' ? icono(ICONOS.cancelado) : null)),
                    h('span', { class: 'bateria-modelo-texto' },
                        h('span', { class: 'bateria-modelo-ref' }, modelo.visible),
                        h('span', { class: 'bateria-modelo-nombre' },
                            modelo.nombre || (modelo.estado === 'PENDIENTE' ? 'sin nombre' : ''))),
                    modelo.sinFotos ? h('span', { class: 'bateria-sin-fotos', title: 'Sin fotos seleccionadas' },
                        icono(ICONOS.sinFotos)) : null,
                    falta ? h('span', { class: 'bateria-falta', title: modelo.faltan.join('\n') },
                        'falta ' + modelo.faltan.length) : null,
                    modelo.existe && !modelo.gris && modelo.estado === 'PENDIENTE' && !falta
                        ? h('span', { class: 'bateria-existe' }, 'ya existe') : null)));
        });
        if (!pintados) {
            lista.appendChild(h('li', { class: 'bateria-separador' },
                lote.modelos.length ? 'Ningún modelo con ese texto' : 'No queda ningún modelo'));
        }
    }

    // --- El panel del modelo ---

    function pintarPanel() {
        const activo = document.activeElement && panel.contains(document.activeElement)
            ? document.activeElement.getAttribute('data-k') : null;
        const desplazamiento = panel.scrollTop;
        panel.textContent = '';
        const modelo = modeloElegido();
        if (!modelo) {
            anadir(panel, lote.modelos.length ? h('p', { class: 'ayuda' }, 'Elige un modelo de la lista.')
                : [h('h3', { class: 'titulo-seccion' }, 'No queda ningún modelo en la carga'),
                    h('p', { class: 'ayuda' }, 'Todo lo aceptado está ya en el sistema.'),
                    h('p', {}, h('a', { class: 'boton', href: BASE }, 'Cargar más modelos'), ' ',
                        h('a', { class: 'boton secundario', href: CATALOGO + '?cliente=' + encodeURIComponent(lote.cliente) },
                            'Ver el catálogo'))]);
            return;
        }
        anadir(panel, [cabeceraDe(modelo), avisosDe(modelo),
            h('div', { class: 'panel-arriba' }, obligatorioDe(modelo), fotosDe(modelo)),
            opcionalDe(modelo)]);
        panel.scrollTop = desplazamiento;
        if (activo) {
            const campo = panel.querySelector('[data-k="' + CSS.escape(activo) + '"]');
            if (campo) { campo.focus(); }
        }
    }

    function cabeceraDe(modelo) {
        const etiquetas = [];
        if (modelo.estado === 'ACEPTADO') { etiquetas.push(h('span', { class: 'chip chip-aceptado' }, 'Aceptado')); }
        if (modelo.estado === 'CANCELADO') { etiquetas.push(h('span', { class: 'chip chip-cancelado' }, 'Cancelado: no se cargará')); }
        if (!modelo.existe) {
            etiquetas.push(h('span', { class: 'chip' }, 'Modelo nuevo'));
        } else {
            etiquetas.push(h('span', { class: 'chip chip-existe' }, 'Ya existe en el catálogo'));
            (modelo.novedades.length ? modelo.novedades : ['no trae nada nuevo']).forEach(function (novedad) {
                etiquetas.push(h('span', { class: 'chip chip-novedad' }, novedad));
            });
        }
        const acciones = [];
        if (modelo.estado === 'PENDIENTE') {
            acciones.push(h('button', { type: 'button', class: 'secundario boton-cancelar-modelo',
                onclick: function () { marcar(modelo, 'CANCELADO'); } }, 'Cancelar Modelo'));
            acciones.push(h('button', { type: 'button', id: 'botonAceptar', class: 'boton-aceptar-modelo',
                disabled: modelo.faltan.length > 0,
                title: modelo.faltan.length ? modelo.faltan.join('\n') : 'Lo marca en verde y pasa al siguiente',
                onclick: function () { marcar(modelo, 'ACEPTADO'); } }, 'Aceptar Modelo'));
        } else {
            acciones.push(h('button', { type: 'button', class: 'secundario',
                onclick: function () { marcar(modelo, 'PENDIENTE'); } },
                modelo.estado === 'ACEPTADO' ? 'Volver a revisarlo' : 'Recuperar el modelo'));
            if (modelo.estado === 'ACEPTADO') {
                acciones.push(h('button', { type: 'button', class: 'secundario boton-cancelar-modelo',
                    onclick: function () { marcar(modelo, 'CANCELADO'); } }, 'Cancelar Modelo'));
            }
        }
        return h('header', { class: 'panel-cabecera', 'data-estado': modelo.estado },
            h('div', { class: 'panel-titulo' },
                h('h2', {}, modelo.visible),
                h('p', { class: 'panel-nombre' }, modelo.nombre || 'Sin nombre todavía'),
                h('p', { class: 'panel-chips' }, etiquetas)),
            h('div', { class: 'panel-acciones' }, acciones));
    }

    function avisosDe(modelo) {
        const avisos = avisosDeModelo[modelo.id];
        return avisos ? h('ul', { class: 'alerta aviso lista-avisos' },
            avisos.map(function (aviso) { return h('li', {}, aviso); })) : null;
    }

    /** Un campo de texto con su rótulo. `fijar(valor)` escribe en el modelo; después se guarda. */
    function campo(modelo, clave, rotulo, valor, fijar, extra) {
        const opciones = extra || {};
        const entrada = h('input', {
            type: 'text', 'data-k': clave, value: valor || '', placeholder: opciones.placeholder,
            list: opciones.lista, inputmode: opciones.numerico ? 'decimal' : null, autocomplete: 'off',
            class: opciones.falta ? 'falta' : null,
            onchange: function () { fijar(entrada.value); guardar(modelo); },
            oninput: opciones.alTeclear ? function () { opciones.alTeclear(entrada.value); } : null
        });
        return h('label', { class: 'campo' + (opciones.ancho ? ' campo-ancho' : '') },
            h('span', { class: 'campo-rotulo' }, rotulo), entrada,
            opciones.ayuda ? h('span', { class: 'campo-ayuda' }, opciones.ayuda) : null);
    }

    function fijo(rotulo, valor) {
        return h('div', { class: 'campo' }, h('span', { class: 'campo-rotulo' }, rotulo),
            h('span', { class: 'campo-fijo' }, valor));
    }

    function desplegable(modelo, clave, rotulo, valor, opciones, fijar) {
        const lista = h('select', { 'data-k': clave,
            onchange: function () { fijar(lista.value); guardar(modelo); } },
            opciones.map(function (opcion) {
                return h('option', { value: opcion.valor, selected: opcion.valor === valor }, opcion.texto);
            }));
        return h('label', { class: 'campo' }, h('span', { class: 'campo-rotulo' }, rotulo), lista);
    }

    /** Dos o cuatro opciones a la vista en vez de un desplegable: un clic menos en lo que se toca siempre. */
    function segmentos(modelo, clave, rotulo, valor, opciones, fijar, ancho) {
        return h('div', { class: 'campo' + (ancho ? ' campo-ancho' : '') },
            h('span', { class: 'campo-rotulo' }, rotulo),
            h('div', { class: 'segmentos', role: 'group', 'aria-label': rotulo },
                opciones.map(function (opcion) {
                    return h('button', {
                        type: 'button', class: 'segmento' + (opcion.valor === valor ? ' activo' : ''),
                        'data-k': clave + ':' + opcion.valor, 'aria-pressed': opcion.valor === valor ? 'true' : 'false',
                        onclick: function () { if (opcion.valor !== valor) { fijar(opcion.valor); guardar(modelo); } }
                    }, opcion.texto);
                })));
    }

    // Mientras se teclea un obligatorio, «Aceptar Modelo» se enciende sin
    // esperar al servidor: si no, al pulsarlo nada más escribir el nombre el
    // botón aún estaría apagado. El servidor lo vuelve a comprobar al aceptar.
    function encenderAceptar(modelo, cambios) {
        const boton = document.getElementById('botonAceptar');
        if (!boton) { return; }
        const datos = Object.assign({ codigo: modelo.codigo, nombrePrototipo: modelo.nombrePrototipo,
            nombre: modelo.nombre }, cambios);
        const identidad = datos.codigo.trim() || (datos.nombrePrototipo.trim() && modelo.temporadaId);
        boton.disabled = !(identidad && datos.nombre.trim());
    }

    function obligatorioDe(modelo) {
        const cliente = clienteDe(modelo);
        const campos = [];
        if (modelo.clienteEditable) {
            campos.push(desplegable(modelo, 'cliente', 'Cliente', modelo.cliente,
                Object.keys(lote.clientes).map(function (clave) {
                    return { valor: clave, texto: lote.clientes[clave].nombre };
                }), function (valor) { modelo.cliente = valor; modelo.temporadaId = null; }));
        } else {
            campos.push(fijo('Cliente', cliente.nombre));
        }
        const faltaIdentidad = !modelo.codigo && !modelo.nombrePrototipo;
        if (modelo.identidadEditable) {
            campos.push(campo(modelo, 'codigo', 'Referencia del modelo', modelo.codigo,
                function (valor) { modelo.codigo = valor; },
                { falta: faltaIdentidad, placeholder: cliente.parteReferencias ? 'ULL027, F67008…' : 'La referencia',
                    alTeclear: function (valor) { encenderAceptar(modelo, { codigo: valor }); } }));
            if (!modelo.codigo || modelo.nombrePrototipo) {
                campos.push(campo(modelo, 'nombrePrototipo', 'o referencia de prototipo', modelo.nombrePrototipo,
                    function (valor) { modelo.nombrePrototipo = valor; },
                    { falta: faltaIdentidad, placeholder: 'PROTO A',
                        ayuda: 'Si el cliente aún no le ha dado referencia. Necesita temporada.',
                        alTeclear: function (valor) { encenderAceptar(modelo, { nombrePrototipo: valor }); } }));
            }
        } else {
            campos.push(fijo('Referencia del modelo', modelo.codigo || modelo.nombrePrototipo));
        }
        campos.push(campo(modelo, 'nombre', 'Nombre del modelo', modelo.nombre,
            function (valor) { modelo.nombre = valor; },
            { falta: !modelo.nombre, ancho: true, placeholder: 'LE NEIGE, CANDY POUCH BAG…',
                alTeclear: function (valor) { encenderAceptar(modelo, { nombre: valor }); } }));
        campos.push(segmentos(modelo, 'tipo', 'Tipo', modelo.tipo,
            [{ valor: 'BOLSO', texto: 'Bolso' }, { valor: 'CINTURON', texto: 'Cinturón' }],
            function (valor) { modelo.tipo = valor; }));
        campos.push(segmentos(modelo, 'etapa', 'Etapa', modelo.etapa, lote.etapas,
            function (valor) { modelo.etapa = valor; }, true));
        return h('section', { class: 'bloque bloque-obligatorio' },
            h('h3', { class: 'bloque-titulo' }, 'Obligatorio'),
            h('div', { class: 'rejilla-campos' }, campos),
            modelo.faltan.length ? h('ul', { class: 'faltan' },
                modelo.faltan.map(function (falta) { return h('li', {}, falta); })) : null);
    }

    // --- Fotos y fichas técnicas ---

    function urlFoto(modelo, foto) {
        return foto.guardada ? FOTO_CATALOGO + foto.fotoCatalogoId
            : BASE + '/modelos/' + modelo.id + '/fotos/' + foto.id;
    }

    function mover(modelo, desde, hasta) {
        if (desde === hasta || hasta < 0 || hasta >= modelo.fotos.length) { return; }
        const foto = modelo.fotos.splice(desde, 1)[0];
        modelo.fotos.splice(hasta, 0, foto);
        guardar(modelo);
    }

    const ORIGEN_FOTO = { FICHA: 'de una ficha', CROQUIS: 'croquis del excel', GUARDADA: 'ya guardada' };

    function fotosDe(modelo) {
        const principal = modelo.fotos.findIndex(function (foto) { return foto.seleccionada && !foto.ilegible; });
        const tira = h('div', { class: 'tira-fotos' });
        modelo.fotos.forEach(function (foto, indice) {
            const imagen = foto.ilegible ? h('span', { class: 'foto-hueco' }, 'No se pudo leer')
                : (foto.lista ? h('img', { src: urlFoto(modelo, foto), alt: foto.nombre, loading: 'lazy', draggable: 'false' })
                    : h('span', { class: 'foto-hueco' }, 'Preparando…'));
            const tarjeta = h('figure', {
                class: 'foto' + (foto.seleccionada ? '' : ' sin-elegir') + (indice === principal ? ' principal' : ''),
                draggable: 'true', title: foto.nombre,
                ondragstart: function (evento) {
                    evento.dataTransfer.setData('text/plain', String(indice));
                    evento.dataTransfer.effectAllowed = 'move';
                    tarjeta.classList.add('arrastrando');
                },
                ondragend: function () { tarjeta.classList.remove('arrastrando'); },
                ondragover: function (evento) { evento.preventDefault(); tarjeta.classList.add('destino'); },
                ondragleave: function () { tarjeta.classList.remove('destino'); },
                ondrop: function (evento) {
                    evento.preventDefault();
                    tarjeta.classList.remove('destino');
                    const desde = parseInt(evento.dataTransfer.getData('text/plain'), 10);
                    if (!isNaN(desde)) { mover(modelo, desde, indice); }
                }
            },
                imagen,
                indice === principal ? h('span', { class: 'foto-principal' }, 'Principal') : null,
                foto.guardada ? null : h('label', { class: 'foto-elegir', title: 'Usar esta foto' },
                    h('input', { type: 'checkbox', checked: foto.seleccionada, 'data-k': 'foto:' + foto.id,
                        disabled: foto.ilegible,
                        onchange: function (evento) { foto.seleccionada = evento.target.checked; guardar(modelo); } }),
                    h('span', {}, 'Usar')),
                h('figcaption', {},
                    h('button', { type: 'button', class: 'foto-mover', disabled: indice === 0,
                        'data-k': 'foto-izq:' + foto.id, title: 'Mover a la izquierda',
                        'aria-label': 'Mover ' + foto.nombre + ' a la izquierda',
                        onclick: function () { mover(modelo, indice, indice - 1); } }, '‹'),
                    h('span', { class: 'foto-nombre' }, ORIGEN_FOTO[foto.origen] || foto.nombre),
                    h('button', { type: 'button', class: 'foto-mover', disabled: indice === modelo.fotos.length - 1,
                        'data-k': 'foto-der:' + foto.id, title: 'Mover a la derecha',
                        'aria-label': 'Mover ' + foto.nombre + ' a la derecha',
                        onclick: function () { mover(modelo, indice, indice + 1); } }, '›')));
            tira.appendChild(tarjeta);
        });
        const selector = h('input', { type: 'file', multiple: true, hidden: true,
            accept: '.jpg,.jpeg,.png,.heic,.heif,.pdf',
            onchange: function () { subir(modelo, selector.files); } });
        tira.appendChild(h('button', { type: 'button', class: 'foto foto-anadir', 'data-k': 'anadir-fotos',
            onclick: function () { selector.click(); } },
            icono(ICONOS.mas, 'icono-grande'), h('span', {}, 'Añadir fotos o fichas en PDF')));

        const fichas = modelo.fichas.map(function (ficha) {
            const elegidas = modelo.fotos.filter(function (foto) { return foto.origen === 'FICHA'; }).length;
            return h('li', { class: 'ficha' },
                icono(ICONOS.pdf),
                h('a', { href: BASE + '/modelos/' + modelo.id + '/fichas/' + ficha.id, target: '_blank',
                    rel: 'noopener', class: 'ficha-nombre' }, ficha.nombre),
                h('button', { type: 'button', class: 'mini', 'data-k': 'ficha:' + ficha.id,
                    onclick: function () { abrirFicha(modelo, ficha); } },
                    modelo.fichas.length === 1 && elegidas ? 'Cambiar sus fotos' : 'Elegir sus fotos'),
                h('button', { type: 'button', class: 'enlace', title: 'Quitar esta ficha y sus fotos',
                    onclick: function () {
                        pedir('DELETE', '/modelos/' + modelo.id + '/fichas/' + ficha.id).then(aplicar, function () {});
                    } }, 'Quitar'));
        });
        return h('section', { class: 'bloque bloque-fotos' },
            h('h3', { class: 'bloque-titulo' }, 'Fotos',
                h('span', { class: 'bloque-nota' }, modelo.fotos.length > 1
                    ? 'Arrástralas para ordenarlas: la primera de la izquierda es la principal.'
                    : (modelo.fotos.length ? 'La primera de la izquierda es la principal.' : 'Opcional.'))),
            selector, tira,
            fichas.length ? [h('h4', { class: 'bloque-subtitulo' }, 'Fichas técnicas'),
                h('ul', { class: 'fichas' }, fichas),
                h('p', { class: 'ayuda' }, 'Las imágenes de una ficha no entran solas: elige cuáles son fotos del modelo.')]
                : null);
    }

    function subir(modelo, ficheros) {
        if (!ficheros || !ficheros.length) { return; }
        const datos = new FormData();
        Array.prototype.forEach.call(ficheros, function (fichero) { datos.append('ficheros', fichero); });
        avisosDeModelo[modelo.id] = ['Subiendo ' + plural(ficheros.length, 'fichero', 'ficheros') + '…'];
        pintarPanel();
        pedir('POST', '/modelos/' + modelo.id + '/ficheros', datos).then(aplicar, function () {
            avisosDeModelo[modelo.id] = null;
            pintarPanel();
        });
    }

    // --- El emergente de una ficha técnica ---

    const dialogoFicha = document.getElementById('dialogoFicha');
    let fichaAbierta = null;

    function abrirFicha(modelo, ficha) {
        const rejilla = document.getElementById('candidatas');
        rejilla.textContent = '';
        rejilla.appendChild(h('p', { class: 'ayuda' }, 'Sacando las imágenes del PDF…'));
        document.getElementById('tituloFicha').textContent = 'Imágenes de ' + ficha.nombre;
        document.getElementById('aceptarFicha').disabled = true;
        fichaAbierta = null;
        dialogoFicha.showModal();
        pedir('GET', '/modelos/' + modelo.id + '/fichas/' + ficha.id + '/candidatas').then(function (respuesta) {
            fichaAbierta = { modelo: modelo, ficha: ficha, loteId: respuesta.loteId };
            rejilla.textContent = '';
            if (!respuesta.candidatas.length) {
                rejilla.appendChild(h('p', { class: 'ayuda' }, 'Este PDF no tiene ninguna imagen que se pueda usar.'));
                return;
            }
            respuesta.candidatas.forEach(function (candidata) {
                rejilla.appendChild(h('label', { class: 'candidata' },
                    h('input', { type: 'checkbox', value: candidata.clave,
                        checked: respuesta.elegidas.indexOf(candidata.clave) >= 0 }),
                    h('img', { src: BASE + '/candidatas/' + respuesta.loteId + '/' + candidata.clave,
                        alt: candidata.origen, loading: 'lazy' }),
                    h('span', { class: 'candidata-pie' },
                        candidata.origen + ', ' + candidata.ancho + ' × ' + candidata.alto)));
            });
            document.getElementById('aceptarFicha').disabled = false;
        }, function (error) {
            rejilla.textContent = '';
            rejilla.appendChild(h('p', { class: 'alerta error' }, error.message));
        });
    }

    document.getElementById('cancelarFicha').addEventListener('click', function () { dialogoFicha.close(); });
    document.getElementById('aceptarFicha').addEventListener('click', function () {
        if (!fichaAbierta) { return; }
        const claves = Array.prototype.map.call(
            dialogoFicha.querySelectorAll('input[type="checkbox"]:checked'),
            function (casilla) { return casilla.value; });
        const abierta = fichaAbierta;
        dialogoFicha.close();
        pedir('POST', '/modelos/' + abierta.modelo.id + '/fichas/' + abierta.ficha.id + '/elegir',
            { loteId: abierta.loteId, claves: claves }).then(aplicar, function () {});
    });

    // --- Opcional: temporada, pieles, colores y embalaje ---

    function opcionalDe(modelo) {
        const cliente = clienteDe(modelo);
        const temporadas = [{ valor: '', texto: '-- sin temporada --' }].concat(cliente.temporadas);
        const contenido = [
            h('div', { class: 'rejilla-campos rejilla-temporada' },
                desplegable(modelo, 'temporadaId', 'Temporada',
                    modelo.temporadaId === null ? '' : String(modelo.temporadaId), temporadas,
                    function (valor) { modelo.temporadaId = valor ? Number(valor) : null; }))
        ];
        if (!modelo.codigo) {
            contenido.push(h('p', { class: 'ayuda' },
                'Las pieles, los colores y el embalaje se guardan por referencia: aparecerán cuando el modelo tenga la suya.'));
        } else {
            contenido.push(pielesDe(modelo, cliente));
        }
        return h('section', { class: 'bloque bloque-opcional' },
            h('h3', { class: 'bloque-titulo' }, 'Opcional'), contenido);
    }

    function pielesDe(modelo, cliente) {
        const pieles = modelo.pieles;
        let abierta = pieles.find(function (piel) { return piel.referencia === pielAbierta[modelo.id]; }) || pieles[0];
        const nodos = [];
        if (cliente.parteReferencias) {
            const nueva = h('input', { type: 'text', 'data-k': 'piel-nueva', class: 'piel-nueva', autocomplete: 'off',
                placeholder: 'Referencia entera de otra piel',
                onkeydown: function (evento) { if (evento.key === 'Enter') { evento.preventDefault(); anadirPiel(); } } });
            const anadirPiel = function () {
                const referencia = nueva.value.trim().toUpperCase();
                if (!referencia) { nueva.focus(); return; }
                if (!pieles.some(function (piel) { return piel.referencia === referencia; })) {
                    pieles.push({ referencia: referencia, refPiel: '', existe: false, nombrePiel: null, forro: null,
                        combinaciones: null, medidaCaja: null, unidadesPorCaja: null, pesoNetoKg: null, colores: [] });
                }
                pielAbierta[modelo.id] = referencia;
                guardar(modelo);
            };
            nodos.push(h('div', { class: 'pestanas', role: 'tablist', 'aria-label': 'Pieles del modelo' },
                pieles.map(function (piel) {
                    return h('button', {
                        type: 'button', role: 'tab', 'data-k': 'pestana:' + piel.referencia,
                        class: 'pestana' + (piel === abierta ? ' activa' : ''),
                        'aria-selected': piel === abierta ? 'true' : 'false', title: piel.referencia,
                        onclick: function () { pielAbierta[modelo.id] = piel.referencia; pintarPanel(); }
                    }, piel.refPiel || piel.referencia, piel.existe ? null : h('span', { class: 'pestana-nueva' }, 'nueva'));
                }),
                h('span', { class: 'pestana-anadir' }, nueva,
                    h('button', { type: 'button', class: 'mini', onclick: anadirPiel }, 'Añadir piel'))));
            if (!pieles.length) {
                nodos.push(h('p', { class: 'ayuda' }, 'Este modelo todavía no tiene ninguna piel. Escribe la referencia '
                    + 'entera de una (por ejemplo ' + modelo.codigo + '.AL0103 o PXCBC-' + modelo.codigo + ') y añádela.'));
            }
        }
        if (abierta) {
            nodos.push(pielDe(modelo, abierta, cliente));
        }
        return nodos;
    }

    function pielDe(modelo, piel, cliente) {
        const k = 'p:' + piel.referencia + ':';
        const combinaciones = [];
        for (let i = 0; i < MAX_COMBINACIONES; i++) {
            // Se enseña una casilla más que las rellenas: las combinaciones van sin huecos.
            if (i > 0 && !piel.combinaciones[i - 1] && !piel.combinaciones[i]) { break; }
            combinaciones.push(campo(modelo, k + 'combinacion' + i, 'Combinación ' + (i + 1), piel.combinaciones[i],
                (function (indice) { return function (valor) { piel.combinaciones[indice] = valor; }; })(i),
                {}));
        }
        const cartones = h('datalist', { id: 'listaCartones' },
            lote.cartones.map(function (carton) { return h('option', { value: carton }); }));
        return h('div', { class: 'piel', role: 'tabpanel' },
            h('p', { class: 'piel-referencia' },
                h('strong', {}, piel.referencia),
                piel.existe ? ' ya está en el catálogo: lo que cambies aquí sustituye lo guardado.'
                    : ' se dará de alta con el modelo.',
                !piel.existe && cliente.parteReferencias ? h('button', { type: 'button', class: 'enlace',
                    onclick: function () {
                        modelo.pieles = modelo.pieles.filter(function (otra) { return otra !== piel; });
                        delete pielAbierta[modelo.id];
                        guardar(modelo);
                    } }, 'Quitar esta piel') : null),
            h('div', { class: 'piel-columnas' },
                h('div', { class: 'piel-grupo' },
                    h('h4', { class: 'bloque-subtitulo' }, 'Piel y composición'),
                    h('div', { class: 'rejilla-campos' },
                        piel.refPiel ? campo(modelo, k + 'nombrePiel', 'Nombre de la piel ' + piel.refPiel,
                            piel.nombrePiel, function (valor) { piel.nombrePiel = valor; },
                            { placeholder: 'NAPA, ALPACA TOUCH…' }) : null,
                        campo(modelo, k + 'forro', 'Forro', piel.forro, function (valor) { piel.forro = valor; }),
                        combinaciones)),
                h('div', { class: 'piel-grupo piel-embalaje' },
                    h('h4', { class: 'bloque-subtitulo' }, 'Embalaje'),
                    cartones,
                    h('div', { class: 'rejilla-campos rejilla-embalaje' },
                        campo(modelo, k + 'medidaCaja', 'Cartón', piel.medidaCaja,
                            function (valor) { piel.medidaCaja = valor; },
                            { lista: 'listaCartones', placeholder: '60x40x40' }),
                        campo(modelo, k + 'unidadesPorCaja', 'Unidades por caja', piel.unidadesPorCaja,
                            function (valor) { piel.unidadesPorCaja = valor; }, { numerico: true }),
                        campo(modelo, k + 'pesoNetoKg', 'Peso neto de la caja llena (kg)', piel.pesoNetoKg,
                            function (valor) { piel.pesoNetoKg = valor; }, { numerico: true })))),
            coloresDe(modelo, piel));
    }

    function coloresDe(modelo, piel) {
        const k = 'p:' + piel.referencia + ':c:';
        const combinaciones = piel.combinaciones.map(function (nombre, indice) { return { nombre: nombre, indice: indice }; })
            .filter(function (combinacion) { return combinacion.nombre; });
        function celda(color, clave, valor, fijar) {
            const entrada = h('input', { type: 'text', 'data-k': k + color.clave + ':' + clave, value: valor || '',
                autocomplete: 'off', 'aria-label': clave + ' del color ' + color.clave,
                onchange: function () { fijar(entrada.value); guardar(modelo); } });
            return h('td', {}, entrada);
        }
        const filas = piel.colores.map(function (color) {
            return h('tr', {},
                h('th', { scope: 'row' }, color.codigo || color.clave,
                    color.existe ? null : h('span', { class: 'pestana-nueva' }, 'nuevo')),
                color.codigo ? celda(color, 'descripcion', color.descripcion,
                    function (valor) { color.descripcion = valor; }) : h('td', { class: 'texto-suave' }, 'sin código'),
                celda(color, 'colorPiel', color.colorPiel, function (valor) { color.colorPiel = valor; }),
                celda(color, 'colorForro', color.colorForro, function (valor) { color.colorForro = valor; }),
                combinaciones.map(function (combinacion) {
                    return celda(color, 'colorCombinacion' + combinacion.indice,
                        color.coloresCombinacion[combinacion.indice],
                        function (valor) { color.coloresCombinacion[combinacion.indice] = valor; });
                }),
                h('td', { class: 'celda-quitar' }, color.existe ? null : h('button', { type: 'button', class: 'enlace',
                    title: 'Quitar este color', 'aria-label': 'Quitar el color ' + color.clave,
                    onclick: function () {
                        piel.colores = piel.colores.filter(function (otro) { return otro !== color; });
                        guardar(modelo);
                    } }, '×')));
        });
        const codigo = h('input', { type: 'text', 'data-k': k + 'nuevo-codigo', placeholder: 'Código', autocomplete: 'off',
            class: 'color-codigo', 'aria-label': 'Código del color nuevo' });
        const descripcion = h('input', { type: 'text', 'data-k': k + 'nuevo-descripcion', placeholder: 'Descripción',
            autocomplete: 'off', 'aria-label': 'Descripción del color nuevo',
            onkeydown: function (evento) { if (evento.key === 'Enter') { evento.preventDefault(); anadirColor(); } } });
        function anadirColor() {
            const elCodigo = codigo.value.trim().toUpperCase();
            const laDescripcion = descripcion.value.trim();
            const clave = elCodigo || laDescripcion.toUpperCase();
            if (!clave) { codigo.focus(); return; }
            if (!piel.colores.some(function (color) { return color.clave === clave; })) {
                piel.colores.push({ clave: clave, codigo: elCodigo, descripcion: laDescripcion, existe: false,
                    colorPiel: null, colorForro: null, coloresCombinacion: null });
            }
            guardar(modelo);
        }
        return h('div', { class: 'piel-colores' },
            h('h4', { class: 'bloque-subtitulo' }, 'Colores del bolso en esta piel'),
            h('table', { class: 'tabla-colores-carga' },
                h('thead', {}, h('tr', {},
                    h('th', {}, 'Color'), h('th', {}, 'Descripción'), h('th', {}, 'Color de la piel'),
                    h('th', {}, 'Color del forro'),
                    combinaciones.map(function (combinacion) { return h('th', {}, 'Color de ' + combinacion.nombre); }),
                    h('th', {}))),
                h('tbody', {}, filas,
                    h('tr', { class: 'fila-color-nuevo' },
                        h('td', {}, codigo), h('td', {}, descripcion),
                        h('td', { colspan: String(2 + combinaciones.length) },
                            h('button', { type: 'button', class: 'mini', onclick: anadirColor }, 'Añadir color'),
                            h('span', { class: 'campo-ayuda' }, ' Sin código, el color se identifica por su descripción.')),
                        h('td', {})))));
    }

    // --- Todo junto ---

    function pintar() {
        if (!lote) { return; }
        pintarBarra();
        pintarAvisos();
        pintarLista();
        pintarPanel();
    }

    /** Mientras las fotos se convierten, se pregunta cómo van; al acabar se vuelve a pedir el lote. */
    function vigilarFotos() {
        clearInterval(sondeo);
        if (!lote || lote.fotos.terminadas) { return; }
        sondeo = setInterval(function () {
            fetch(BASE + '/progreso', { headers: { Accept: 'application/json' } }).then(function (respuesta) {
                return respuesta.ok ? respuesta.json() : null;
            }).then(function (progreso) {
                if (!progreso || !lote) { return; }
                lote.fotos = progreso;
                pintarBarra();
                if (progreso.terminadas) {
                    clearInterval(sondeo);
                    recargar();
                }
            }).catch(function () { /* se vuelve a preguntar en la siguiente vuelta */ });
        }, 1500);
    }

    document.getElementById('filtro').addEventListener('input', function (evento) {
        filtro = evento.target.value;
        pintarLista();
    });

    document.getElementById('botonAnadir').addEventListener('click', function () {
        const actual = modeloElegido();
        pedir('POST', '/modelos' + (actual ? '?cliente=' + encodeURIComponent(actual.cliente) : ''))
            .then(function (respuesta) {
                lote = respuesta.lote;
                elegido = respuesta.id;
                pintar();
                const referencia = panel.querySelector('[data-k="codigo"]');
                if (referencia) { referencia.focus(); }
            }, function () {});
    });

    document.getElementById('botonAceptarCompletos').addEventListener('click', function () {
        pedir('POST', '/aceptar-completos').then(function (estado) {
            lote = estado;
            const actual = modeloElegido();
            if (!actual || actual.estado !== 'PENDIENTE') { elegido = primeroPorRevisar(elegido); }
            pintar();
        }, function () {});
    });

    const dialogoResumen = document.getElementById('dialogoResumen');
    document.getElementById('cerrarResumen').addEventListener('click', function () { dialogoResumen.close(); });

    document.getElementById('botonCargar').addEventListener('click', function () {
        const grises = lote.modelos.filter(function (modelo) { return modelo.gris && modelo.estado === 'PENDIENTE'; }).length;
        const porRevisar = lote.cuentas.pendientes - grises;
        if (porRevisar > 0 && !confirm((porRevisar === 1 ? 'Queda 1 modelo' : 'Quedan ' + porRevisar + ' modelos')
                + ' sin aceptar ni cancelar: no se cargan y siguen en la lista.\n\n¿Cargar al sistema los '
                + lote.cuentas.aceptados + ' aceptados?')) {
            return;
        }
        const boton = document.getElementById('botonCargar');
        boton.disabled = true;
        boton.textContent = 'Cargando…';
        const cliente = lote.cliente;
        pedir('POST', '/cargar').then(function (respuesta) {
            lote = respuesta.lote;
            elegido = primeroPorRevisar();
            pintar();
            const resumen = respuesta.resumen;
            const lineas = [];
            if (resumen.modelosNuevos) { lineas.push(plural(resumen.modelosNuevos, 'modelo nuevo', 'modelos nuevos')); }
            if (resumen.modelosCompletados) {
                lineas.push(plural(resumen.modelosCompletados, 'modelo que ya existía, completado',
                    'modelos que ya existían, completados'));
            }
            if (resumen.pieles) { lineas.push(plural(resumen.pieles, 'piel nueva', 'pieles nuevas')); }
            if (resumen.colores) { lineas.push(plural(resumen.colores, 'color nuevo', 'colores nuevos')); }
            if (resumen.fotos) { lineas.push(plural(resumen.fotos, 'foto', 'fotos')); }
            if (resumen.fichas) { lineas.push(plural(resumen.fichas, 'ficha técnica', 'fichas técnicas')); }
            const texto = document.getElementById('textoResumen');
            texto.textContent = '';
            anadir(texto, [h('ul', {}, lineas.map(function (linea) { return h('li', {}, linea); })),
                resumen.avisos.length ? h('ul', { class: 'alerta aviso lista-avisos' },
                    resumen.avisos.map(function (aviso) { return h('li', {}, aviso); })) : null,
                lote.modelos.length ? h('p', { class: 'ayuda' },
                    plural(lote.modelos.length, 'modelo sigue', 'modelos siguen') + ' en la lista, sin cargar.') : null]);
            document.getElementById('verCatalogo').href = CATALOGO + '?cliente=' + encodeURIComponent(cliente);
            dialogoResumen.showModal();
        }, function () { pintarBarra(); });
    });

    // La sesión no debe caducar mientras se revisa el lote.
    setInterval(function () {
        if (!caducada) { fetch(BASE + '/latido', { method: 'POST' }).catch(function () {}); }
    }, 5 * 60 * 1000);

    recargar();
})();

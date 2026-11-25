// La pantalla de Hojas de Producción. Todo pasa en la misma página:
//
// - Cliente, temporada guardada y artículo (como en la info de embalaje, sin
//   excel de pedido): el artículo rellena el modelo, el nombre del bolso, la
//   foto y el primer color, y llena los desplegables de piel de cada color con
//   las pieles enlazadas al modelo.
// - Los «campos sugeridos»: un texto con un desplegable pegado debajo; elegir
//   escribe en el texto y el texto manda (el desplegable se resincroniza).
// - Las cargas automáticas: el cliente pone el nombre del marcado, el taller
//   el marcado interior y el número de hoja, el color del marcado y el de la
//   fornitura se copian el uno al otro la primera vez. Cada campo que cambia
//   solo «late» con un borde coral para que se vea qué se ha tocado.
// - La foto: la del catálogo o una subida si el modelo no tiene.
// - Generar por fetch: descarga el excel sin recargar.
//
// Lee su configuración de window.HP (la pone la plantilla).
(function () {
    'use strict';
    var HP = window.HP || {};
    var NADA = '-';

    var form = document.getElementById('formHoja');
    if (!form) return;
    var lineas = document.getElementById('lineas');
    var plantilla = document.getElementById('plantillaLinea');
    var cliente = document.getElementById('cliente');
    var temporada = document.getElementById('temporada');
    var temporadaGuardada = document.getElementById('temporadaGuardada');
    var articulo = document.getElementById('articulo');
    var modelo = document.getElementById('modelo');
    var codigoModelo = document.getElementById('codigoModelo');
    var nombreBolso = document.getElementById('nombreBolso');
    var taller = document.getElementById('taller');
    var tallerSugerido = document.getElementById('tallerSugerido');
    var numero = document.getElementById('numero');

    /** Lo cargado del catálogo: artículos del desplegable y pieles de cada modelo. */
    var catalogo = {articulos: [], modelos: {}};
    var clienteAnterior = cliente.value;

    // --- Utilidades ---

    /** El borde coral que late en un campo que se acaba de rellenar solo. */
    function latir(elemento) {
        if (!elemento) return;
        elemento.classList.remove('hp-latido');
        void elemento.offsetWidth; // reinicia la animación si ya estaba latiendo
        elemento.classList.add('hp-latido');
    }
    document.addEventListener('animationend', function (evento) {
        if (evento.animationName === 'hp-latido') evento.target.classList.remove('hp-latido');
    });

    /** Mayúsculas, sin espacios alrededor ni acentos: «Antonio Muñiz» = «ANTONIO MUNIZ». */
    function normalizar(texto) {
        return (texto || '').normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toUpperCase();
    }

    function campo(linea, nombre) {
        return linea.querySelector('[data-campo="' + nombre + '"]');
    }

    function sugerenciaDe(input) {
        var contenedor = input.closest('.campo-sugerido');
        return contenedor && contenedor.querySelector('select.hp-sugerencia');
    }

    /** Pone en el desplegable la opción que coincide con el texto, o «elegir…» si ninguna. */
    function sincronizar(input) {
        var select = sugerenciaDe(input);
        if (!select || select === temporadaGuardada) return;
        var buscado = normalizar(input.value);
        var opcion = Array.prototype.find.call(select.options, function (o) {
            return o.value !== '' && normalizar(o.value) === buscado;
        });
        select.value = opcion ? opcion.value : '';
    }

    /**
     * Escribe un valor cargado solo: en el texto Y en su desplegable, y hace
     * latir el texto si ha cambiado. Devuelve si ha cambiado.
     */
    function poner(input, valor, conLatido) {
        if (!input) return false;
        valor = valor == null ? '' : String(valor);
        var cambia = input.value !== valor;
        input.value = valor;
        sincronizar(input);
        if (cambia && conLatido !== false) latir(input);
        return cambia;
    }

    /** Rehace las opciones de un desplegable de sugerencias; late si la lista cambia. */
    function opciones(select, valores, textoVacio, conLatido) {
        var antes = Array.prototype.map.call(select.options, function (o) { return o.value; }).join('|');
        select.innerHTML = '';
        var vacia = document.createElement('option');
        vacia.value = '';
        vacia.textContent = textoVacio || 'elegir…';
        select.appendChild(vacia);
        valores.forEach(function (valor) {
            var opcion = document.createElement('option');
            opcion.value = valor;
            opcion.textContent = valor;
            select.appendChild(opcion);
        });
        var despues = Array.prototype.map.call(select.options, function (o) { return o.value; }).join('|');
        if (conLatido && antes !== despues && valores.length) latir(select);
    }

    // --- Marcado exterior: la misma regla que LineaProduccion.marcadoExterior ---
    function marcadoExterior(nombre, color, tamano) {
        var partes = [nombre, color, tamano].map(function (p) { return (p || '').trim(); })
            .filter(function (p) { return p !== '' && p !== NADA; });
        return partes.length ? partes.join(' ') : NADA;
    }

    function pintarVistaMarcado(linea) {
        var vista = linea.querySelector('.hp-marcado-vista');
        if (vista) {
            vista.textContent = marcadoExterior(campo(linea, 'marcadoNombre').value,
                campo(linea, 'marcadoColor').value, campo(linea, 'marcadoTamano').value);
        }
    }

    // --- Cliente: nombre y tamaño del marcado ---
    function rellenarTamanos(linea) {
        var select = linea.querySelector('select.hp-tamanos');
        opciones(select, HP.tamanosPorCliente[cliente.value] || [], 'elegir…', false);
        sincronizar(campo(linea, 'marcadoTamano'));
    }

    // El nombre del marcado arranca con el cliente y el tamaño con el suyo por
    // defecto; al cambiar de cliente solo se pisan si seguían diciendo lo del
    // cliente anterior (o estaban vacíos): lo tecleado a mano se respeta.
    function aplicarCliente(linea, anterior, conLatido) {
        var nombre = campo(linea, 'marcadoNombre');
        var tamano = campo(linea, 'marcadoTamano');
        if (nombre.value.trim() === '' || nombre.value.trim() === anterior) {
            poner(nombre, cliente.value, conLatido);
        }
        var porDefectoAnterior = anterior ? (HP.tamanoPorDefecto[anterior] || '') : '';
        if (tamano.value.trim() === '' || tamano.value.trim() === porDefectoAnterior) {
            poner(tamano, HP.tamanoPorDefecto[cliente.value] || '', conLatido);
        }
        rellenarTamanos(linea);
        pintarVistaMarcado(linea);
    }

    // --- Taller: marcado interior y número de hoja ---
    function marcadoDelTaller() {
        var clave = normalizar(taller.value);
        var encontrado = '';
        Object.keys(HP.marcadoPorTaller || {}).forEach(function (nombre) {
            if (normalizar(nombre) === clave) encontrado = HP.marcadoPorTaller[nombre];
        });
        return encontrado;
    }

    // Solo se pisa un marcado interior vacío o el que puso el taller anterior
    // (data-auto); lo elegido a mano se respeta.
    function aplicarTaller(linea, conLatido) {
        var valor = marcadoDelTaller();
        var interior = campo(linea, 'marcadoInterior');
        if (!valor) return;
        if (interior.value.trim() === '' || interior.dataset.auto === interior.value) {
            poner(interior, valor, conLatido);
            interior.dataset.auto = valor;
        }
    }

    function proponerNumero(conLatido) {
        var clave = taller.value.trim().toUpperCase();
        poner(numero, clave ? (HP.siguientesPorTaller[clave] || 1) : '', conLatido);
    }

    function cambioDeTaller() {
        sincronizar(taller);
        proponerNumero(true);
        todasLasLineas().forEach(function (linea) { aplicarTaller(linea, true); });
    }

    // --- Temporadas y artículos del catálogo ---
    function rellenarTemporadas(conLatido) {
        var lista = (HP.temporadas[cliente.value] || []);
        temporadaGuardada.innerHTML = '';
        var todo = document.createElement('option');
        todo.value = '';
        todo.textContent = cliente.value ? 'todo el catálogo del cliente' : 'elige el cliente';
        temporadaGuardada.appendChild(todo);
        lista.forEach(function (t) {
            var opcion = document.createElement('option');
            opcion.value = t.id;
            opcion.textContent = t.temporada;
            temporadaGuardada.appendChild(opcion);
        });
        temporadaGuardada.disabled = !cliente.value;
        if (conLatido && lista.length) latir(temporadaGuardada);
    }

    var peticion = 0;
    function cargarCatalogo(conLatido) {
        var esta = ++peticion;
        catalogo = {articulos: [], modelos: {}};
        pintarArticulos(false);
        if (!cliente.value) return Promise.resolve();
        var url = HP.urlCatalogo + '?cliente=' + encodeURIComponent(cliente.value)
            + (temporadaGuardada.value ? '&temporadaId=' + encodeURIComponent(temporadaGuardada.value) : '');
        articulo.disabled = true;
        articulo.options[0].textContent = 'Cargando artículos…';
        return fetch(url)
            .then(function (r) { return r.ok ? r.json() : {articulos: [], modelos: {}}; })
            .catch(function () { return {articulos: [], modelos: {}}; })
            .then(function (datos) {
                if (esta !== peticion) return;
                catalogo = datos;
                pintarArticulos(conLatido);
                todasLasLineas().forEach(function (linea) { rellenarPieles(linea, false); });
            });
    }

    function pintarArticulos(conLatido) {
        articulo.innerHTML = '';
        var vacia = document.createElement('option');
        vacia.value = '';
        vacia.textContent = !cliente.value ? 'Elige el cliente para ver sus artículos'
            : (catalogo.articulos.length ? '-- elige un artículo --'
                : 'El catálogo no tiene artículos ' + (temporadaGuardada.value ? 'en esta temporada' : 'de este cliente'));
        articulo.appendChild(vacia);
        catalogo.articulos.forEach(function (a, i) {
            var opcion = document.createElement('option');
            opcion.value = String(i);
            opcion.textContent = a.texto;
            articulo.appendChild(opcion);
        });
        articulo.disabled = catalogo.articulos.length === 0;
        if (conLatido && catalogo.articulos.length) latir(articulo);
    }

    /** El código del modelo de la hoja: el del artículo elegido o, si no, el modelo tecleado. */
    function modeloActual() {
        return codigoModelo.value || modelo.value.trim();
    }

    function pielesDelModelo(tipo) {
        var buscado = normalizar(modeloActual());
        var encontrado = null;
        Object.keys(catalogo.modelos || {}).forEach(function (codigo) {
            if (normalizar(codigo) === buscado) encontrado = catalogo.modelos[codigo];
        });
        return encontrado ? (encontrado[tipo] || []) : [];
    }

    // Los desplegables de piel de un color: las pieles enlazadas al modelo; y
    // los de color, los colores de la piel escrita encima. Solo llenan el
    // desplegable: no escriben en el texto (petición del usuario).
    function rellenarPieles(linea, conLatido) {
        linea.querySelectorAll('select.hp-pieles').forEach(function (select) {
            var pieles = pielesDelModelo(select.dataset.tipo);
            opciones(select, pieles.map(function (p) { return p.nombre; }),
                pieles.length ? 'elegir…' : 'sin pieles en el catálogo', conLatido);
            sincronizar(select.closest('.campo-sugerido').querySelector('input'));
            rellenarColores(select, conLatido);
        });
    }

    function rellenarColores(selectPiel, conLatido) {
        var linea = selectPiel.closest('.hp-linea');
        var pielEscrita = normalizar(selectPiel.closest('.campo-sugerido').querySelector('input').value);
        var piel = pielesDelModelo(selectPiel.dataset.tipo).find(function (p) {
            return normalizar(p.nombre) === pielEscrita;
        });
        var inputColor = campo(linea, selectPiel.dataset.colores);
        opciones(sugerenciaDe(inputColor), piel ? piel.colores : [], piel ? 'elegir…' : 'elige antes la piel', conLatido);
        sincronizar(inputColor);
    }

    // Elegir un artículo: la cabecera (modelo, nombre, foto) y el primer color
    // (pieles, colores y forro). El resto de colores conserva lo suyo y solo
    // recibe las pieles del modelo en sus desplegables.
    function aplicarArticulo(a) {
        codigoModelo.value = a.codigoModelo || '';
        poner(modelo, a.codigoModelo);
        if (a.nombreBolso) poner(nombreBolso, a.nombreBolso);
        fotoDelArticulo(a);
        var primera = todasLasLineas()[0];
        poner(campo(primera, 'pielBase'), a.pielBase);
        poner(campo(primera, 'colorBase'), a.colorBase);
        poner(campo(primera, 'pielCombinacion'), a.pielCombinacion);
        poner(campo(primera, 'colorCombinacion'), a.colorCombinacion);
        poner(campo(primera, 'forro'), a.forro);
        todasLasLineas().forEach(function (linea) { rellenarPieles(linea, true); });
    }

    // --- Foto: la del catálogo, la de la hoja importada o una subida ---
    var fotoImg = document.getElementById('foto');
    var fotoVacia = document.getElementById('fotoVacia');
    var fotoVaciaTexto = document.getElementById('fotoVaciaTexto');
    var fotoSubida = document.getElementById('fotoSubida');
    var fotoId = document.getElementById('fotoId');
    var fotoHojaId = document.getElementById('fotoHojaId');
    var cambiarFoto = document.getElementById('cambiarFoto');
    var quitarFotoSubida = document.getElementById('quitarFotoSubida');
    var panelFoto = document.getElementById('panelFoto');
    var urlSubida = null;

    function pintarFoto() {
        var url = null;
        var subida = fotoSubida.files && fotoSubida.files[0];
        if (urlSubida) { URL.revokeObjectURL(urlSubida); urlSubida = null; }
        if (subida) {
            urlSubida = URL.createObjectURL(subida);
            url = urlSubida;
        } else if (fotoHojaId.value) {
            url = HP.urlFotoHoja + fotoHojaId.value + '/foto';
        } else if (fotoId.value) {
            url = HP.urlFotoCatalogo + fotoId.value;
        }
        fotoImg.hidden = !url;
        if (url) fotoImg.src = url; else fotoImg.removeAttribute('src');
        fotoVacia.hidden = !!url;
        fotoVaciaTexto.textContent = modeloActual() ? 'Este modelo no tiene foto en el catálogo.'
            : 'Elige el artículo para ver su foto, o sube una.';
        cambiarFoto.hidden = !url;
        quitarFotoSubida.hidden = !subida;
    }

    function fotoDelArticulo(a) {
        fotoHojaId.value = '';
        if (a.fotoId != null) {
            fotoId.value = a.fotoId;
            fotoSubida.value = '';
        } else {
            fotoId.value = '';
        }
        pintarFoto();
        latir(panelFoto);
    }

    document.getElementById('subirFoto').addEventListener('click', function () { fotoSubida.click(); });
    cambiarFoto.addEventListener('click', function () { fotoSubida.click(); });
    quitarFotoSubida.addEventListener('click', function () {
        fotoSubida.value = '';
        pintarFoto();
    });
    fotoSubida.addEventListener('change', function () {
        pintarFoto();
        latir(panelFoto);
    });

    // --- Líneas (colores) ---
    function todasLasLineas() {
        return Array.prototype.slice.call(lineas.querySelectorAll('.hp-linea'));
    }

    // Los nombres se rehacen tras cada alta o baja: Spring une lineas[i] por
    // índice y un hueco llegaría como línea vacía.
    function renumerar() {
        todasLasLineas().forEach(function (linea, i) {
            linea.querySelector('.hp-linea-numero').textContent = i + 1;
            linea.querySelectorAll('[data-campo]').forEach(function (c) {
                c.name = 'lineas[' + i + '].' + c.dataset.campo;
            });
        });
    }

    function prepararLinea(linea) {
        rellenarTamanos(linea);
        rellenarPieles(linea, false);
        linea.querySelectorAll('.campo-sugerido input').forEach(sincronizar);
        pintarVistaMarcado(linea);
    }

    function anadirLinea(desde) {
        var nueva = plantilla.content.firstElementChild.cloneNode(true);
        if (desde) {
            nueva.querySelectorAll('[data-campo]').forEach(function (c) {
                var origen = campo(desde, c.dataset.campo);
                c.value = origen ? origen.value : '';
                if (origen && origen.dataset.auto) c.dataset.auto = origen.dataset.auto;
            });
            desde.insertAdjacentElement('afterend', nueva);
        } else {
            lineas.appendChild(nueva);
            aplicarCliente(nueva, '', false);
            aplicarTaller(nueva, false);
        }
        prepararLinea(nueva);
        renumerar();
        return nueva;
    }

    lineas.addEventListener('click', function (evento) {
        var linea = evento.target.closest('.hp-linea');
        if (!linea) return;
        if (evento.target.closest('.hp-duplicar')) {
            anadirLinea(linea).querySelector('[data-campo="unidades"]').focus();
        } else if (evento.target.closest('.hp-quitar')) {
            // El último no se quita: se vacía, para que siempre haya dónde escribir.
            if (todasLasLineas().length === 1) {
                linea.querySelectorAll('[data-campo]').forEach(function (c) { c.value = ''; delete c.dataset.auto; });
                aplicarCliente(linea, '', false);
                aplicarTaller(linea, false);
                prepararLinea(linea);
            } else {
                linea.remove();
                renumerar();
            }
        }
    });

    // Elegir en un desplegable escribe en su texto; los de piel cargan además
    // los colores de esa piel; el color del marcado y el de la fornitura se
    // copian al otro si aún está vacío (la primera vez).
    lineas.addEventListener('change', function (evento) {
        var select = evento.target;
        if (!select.classList.contains('hp-sugerencia') || select.value === '') return;
        var linea = select.closest('.hp-linea');
        var input = select.closest('.campo-sugerido').querySelector('input');
        input.value = select.value;
        delete input.dataset.auto;
        if (select.classList.contains('hp-pieles')) {
            rellenarColores(select, true);
        }
        if (select.dataset.pareja && select.value !== NADA) {
            var pareja = campo(linea, select.dataset.pareja);
            if (pareja.value.trim() === '') poner(pareja, select.value);
        }
        pintarVistaMarcado(linea);
    });

    lineas.addEventListener('input', function (evento) {
        var input = evento.target;
        delete input.dataset.auto;
        if (input.closest('.campo-sugerido')) sincronizar(input);
        var select = sugerenciaDe(input);
        if (select && select.classList.contains('hp-pieles')) rellenarColores(select, false);
        if (input.closest('.hp-g-exterior')) pintarVistaMarcado(input.closest('.hp-linea'));
    });

    document.getElementById('anadirLinea').addEventListener('click', function () {
        anadirLinea(null).querySelector('[data-campo="unidades"]').focus();
    });

    // --- Cabecera ---
    cliente.addEventListener('change', function () {
        todasLasLineas().forEach(function (linea) { aplicarCliente(linea, clienteAnterior, true); });
        clienteAnterior = cliente.value;
        rellenarTemporadas(true);
        cargarCatalogo(true);
    });

    temporadaGuardada.addEventListener('change', function () {
        var elegida = temporadaGuardada.options[temporadaGuardada.selectedIndex];
        if (temporadaGuardada.value) poner(temporada, elegida.textContent);
        cargarCatalogo(true);
    });

    articulo.addEventListener('change', function () {
        var a = catalogo.articulos[articulo.value];
        if (a) aplicarArticulo(a);
    });

    // Un modelo tecleado a mano ya no es el del artículo elegido.
    modelo.addEventListener('input', function () {
        codigoModelo.value = '';
        todasLasLineas().forEach(function (linea) { rellenarPieles(linea, false); });
        pintarFoto();
    });

    taller.addEventListener('input', cambioDeTaller);
    tallerSugerido.addEventListener('change', function () {
        if (!tallerSugerido.value) return;
        taller.value = tallerSugerido.value;
        cambioDeTaller();
    });

    var usarSiguiente = document.getElementById('usarSiguiente');
    if (usarSiguiente) usarSiguiente.addEventListener('click', function () { proponerNumero(true); });

    // --- Generar: guarda y descarga sin recargar ---
    var boton = document.getElementById('botonGenerar');
    var resultado = document.getElementById('resultadoGenerar');

    function avisar(clase, texto) {
        resultado.innerHTML = '';
        var caja = document.createElement('div');
        caja.className = 'alerta ' + clase;
        caja.textContent = texto;
        resultado.appendChild(caja);
    }

    function nombreDeFichero(cabecera) {
        if (cabecera) {
            var utf8 = /filename\*=UTF-8''([^;]+)/i.exec(cabecera);
            if (utf8) return decodeURIComponent(utf8[1]);
            var simple = /filename="([^"]+)"/i.exec(cabecera);
            if (simple) return simple[1];
        }
        return 'Hoja de produccion.xlsx';
    }

    // Enter en un campo no envía: generar guarda y gasta un número.
    form.addEventListener('keydown', function (evento) {
        if (evento.key === 'Enter' && evento.target.tagName === 'INPUT') evento.preventDefault();
    });

    form.addEventListener('submit', function (evento) {
        evento.preventDefault();
        if (!cliente.value) {
            avisar('error', 'Elige el cliente.');
            cliente.focus();
            return;
        }
        if (!taller.value.trim()) {
            avisar('error', 'Escribe el taller: cada taller lleva su numeración.');
            taller.focus();
            return;
        }
        boton.disabled = true;
        boton.textContent = 'Generando…';
        var tallerEnviado = taller.value.trim();
        fetch(form.action, {method: 'POST', body: new FormData(form)})
            .then(function (respuesta) {
                if (!respuesta.ok) {
                    var tipo = respuesta.headers.get('Content-Type') || '';
                    return (tipo.indexOf('text/plain') === 0 ? respuesta.text() : Promise.resolve(''))
                        .then(function (texto) {
                            throw new Error(texto || 'No se ha podido generar la hoja (código ' + respuesta.status + ')');
                        });
                }
                return respuesta.blob().then(function (blob) {
                    return {
                        blob: blob,
                        nombre: nombreDeFichero(respuesta.headers.get('Content-Disposition')),
                        numero: respuesta.headers.get('X-Numero'),
                        siguiente: respuesta.headers.get('X-Siguiente-Numero')
                    };
                });
            })
            .then(function (hoja) {
                var enlace = document.createElement('a');
                enlace.href = URL.createObjectURL(hoja.blob);
                enlace.download = hoja.nombre;
                document.body.appendChild(enlace);
                enlace.click();
                enlace.remove();
                setTimeout(function () { URL.revokeObjectURL(enlace.href); }, 10000);
                avisar('ok', 'Guardada como hoja ' + hoja.numero + ' de ' + tallerEnviado + '. Se ha descargado «' + hoja.nombre + '».');
                var siguiente = parseInt(hoja.siguiente, 10);
                if (!isNaN(siguiente)) HP.siguientesPorTaller[tallerEnviado.toUpperCase()] = siguiente;
                // La siguiente hoja de este taller ya es otra: se propone su número.
                proponerNumero(true);
            })
            .catch(function (error) {
                avisar('error', error.message || 'No se ha podido hablar con el servidor');
            })
            .then(function () {
                boton.disabled = false;
                boton.textContent = 'Generar hoja de producción';
            });
    });

    // --- Arranque ---
    rellenarTemporadas(false);
    // Una hoja importada vuelve con su temporada elegida si sigue guardada,
    // para que sus desplegables de piel tengan las del modelo.
    if (HP.conImportada && temporada.value.trim()) {
        var guardada = Array.prototype.find.call(temporadaGuardada.options, function (o) {
            return o.value && normalizar(o.textContent) === normalizar(temporada.value);
        });
        if (guardada) temporadaGuardada.value = guardada.value;
    }
    todasLasLineas().forEach(function (linea) {
        if (!HP.conImportada) {
            aplicarCliente(linea, '', false);
            aplicarTaller(linea, false);
        }
        prepararLinea(linea);
    });
    sincronizar(taller);
    renumerar();
    pintarFoto();
    cargarCatalogo(false);
    // Sin hoja importada, el taller que ya traiga el campo (el navegador lo
    // puede recordar) propone su número.
    if (!HP.conImportada && taller.value.trim()) proponerNumero(false);
})();

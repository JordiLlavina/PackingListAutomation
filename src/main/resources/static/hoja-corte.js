// La pantalla de la Hoja de corte (Documentos del Corte → Hoja de corte).
// Todo pasa en la misma página, como en la Hoja de Producción:
//
// - Cliente, temporada guardada y artículo del catálogo: el artículo rellena
//   la cabecera (modelo, referencia, nombre del bolso, foto) y la línea de su
//   color (piel, forro, combinaciones y sus colores). «Añadir todos los
//   colores de esta referencia» trae los demás colores que conoce el catálogo.
// - «Empezar desde cero» lo vacía todo, para un modelo que no está en el sistema.
// - La foto: la del catálogo, la de la hoja importada o una subida.
// - Generar por fetch: descarga el excel sin recargar (la hoja se guarda).
//
// Lee su configuración de window.HC (la pone la plantilla).
(function () {
    'use strict';
    var HC = window.HC || {};
    var form = document.getElementById('formHojaCorte');
    if (!form) return;

    var cliente = document.getElementById('cliente');
    var temporada = document.getElementById('temporada');
    var temporadaGuardada = document.getElementById('temporadaGuardada');
    var articulo = document.getElementById('articulo');
    var modelo = document.getElementById('modelo');
    var referencia = document.getElementById('referencia');
    var nombreBolso = document.getElementById('nombreBolso');
    var codigoModelo = document.getElementById('codigoModelo');
    var lineas = document.getElementById('lineas');
    var plantilla = document.getElementById('plantillaLinea');
    var todosLosColores = document.getElementById('todosLosColores');
    var resultado = document.getElementById('resultadoGenerar');

    var catalogo = {articulos: []};
    var elegido = null;

    function normalizar(texto) {
        return (texto || '').normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toUpperCase();
    }

    function campo(linea, nombre) {
        return linea.querySelector('[data-campo="' + nombre + '"]');
    }

    function todasLasLineas() {
        return Array.prototype.slice.call(lineas.rows);
    }

    // --- Líneas de color ---

    function anadirLinea() {
        lineas.insertAdjacentHTML('beforeend', plantilla.innerHTML.replace(/__i__/g, String(lineas.rows.length)));
        return lineas.rows[lineas.rows.length - 1];
    }

    function estaVacia(linea) {
        return ['codigoColor', 'descripcionColor', 'cantidad', 'piel', 'forro'].every(function (nombre) {
            return !campo(linea, nombre).value.trim();
        });
    }

    /** La línea del color del artículo: la que ya lo tiene, una vacía o una nueva. */
    function lineaPara(a) {
        var codigo = normalizar(a.codigoColor);
        var descripcion = normalizar(a.descripcionColor);
        var existente = todasLasLineas().find(function (linea) {
            var suyo = normalizar(campo(linea, 'codigoColor').value);
            return codigo ? suyo === codigo
                : !suyo && normalizar(campo(linea, 'descripcionColor').value) === descripcion;
        });
        return existente || todasLasLineas().find(estaVacia) || anadirLinea();
    }

    /** Escribe lo que sabe el catálogo de un artículo en la línea de su color; la cantidad no se toca. */
    function rellenarLinea(a) {
        var linea = lineaPara(a);
        campo(linea, 'codigoColor').value = a.codigoColor || '';
        campo(linea, 'descripcionColor').value = a.descripcionColor || '';
        campo(linea, 'piel').value = a.piel || '';
        campo(linea, 'colorPiel').value = a.colorPiel || '';
        campo(linea, 'forro').value = a.forro || '';
        campo(linea, 'colorForro').value = a.colorForro || '';
        var combinaciones = a.combinaciones || [];
        for (var i = 0; i < 4; i++) {
            campo(linea, 'comb' + i).value = combinaciones[i] ? combinaciones[i].nombre : '';
            campo(linea, 'colorComb' + i).value = combinaciones[i] ? combinaciones[i].color : '';
        }
        var detalles = linea.querySelector('details');
        detalles.open = combinaciones.length > 0;
        detalles.querySelector('summary').textContent = combinaciones.length
            ? combinaciones.length + ' combinación(es)' : '+ combinaciones';
        return linea;
    }

    document.getElementById('anadirLinea').addEventListener('click', function () {
        anadirLinea().querySelector('input').focus();
    });
    lineas.addEventListener('click', function (evento) {
        var boton = evento.target.closest('.quitar-linea');
        if (!boton) return;
        boton.closest('tr').remove();
        if (!lineas.rows.length) anadirLinea();
    });
    form.addEventListener('keydown', function (evento) {
        if (evento.key === 'Enter' && evento.target.tagName === 'INPUT') evento.preventDefault();
    });

    // --- Temporadas y artículos del catálogo ---

    function rellenarTemporadas() {
        var lista = HC.temporadas[cliente.value] || [];
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
    }

    var peticion = 0;
    function cargarCatalogo() {
        var esta = ++peticion;
        catalogo = {articulos: []};
        pintarArticulos();
        if (!cliente.value) return;
        articulo.options[0].textContent = 'Cargando artículos…';
        var url = HC.urlCatalogo + '?cliente=' + encodeURIComponent(cliente.value)
            + (temporadaGuardada.value ? '&temporadaId=' + encodeURIComponent(temporadaGuardada.value) : '');
        fetch(url)
            .then(function (r) { return r.ok ? r.json() : {articulos: []}; })
            .catch(function () { return {articulos: []}; })
            .then(function (datos) {
                if (esta !== peticion) return;
                catalogo = datos;
                pintarArticulos();
            });
    }

    function pintarArticulos() {
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
    }

    function hermanos(a) {
        return catalogo.articulos.filter(function (otro) {
            return otro !== a && normalizar(otro.referencia) === normalizar(a.referencia);
        });
    }

    function aplicarArticulo(a) {
        elegido = a;
        codigoModelo.value = a.codigoModelo || '';
        modelo.value = a.codigoModelo || '';
        referencia.value = a.referencia || '';
        if (a.nombreBolso) nombreBolso.value = a.nombreBolso;
        fotoHojaId.value = '';
        fotoId.value = a.fotoId != null ? a.fotoId : '';
        pintarFoto();
        rellenarLinea(a).querySelector('[data-campo="cantidad"]').focus();
        todosLosColores.hidden = hermanos(a).length === 0;
    }

    cliente.addEventListener('change', function () {
        rellenarTemporadas();
        cargarCatalogo();
    });
    temporadaGuardada.addEventListener('change', function () {
        var opcion = temporadaGuardada.options[temporadaGuardada.selectedIndex];
        if (temporadaGuardada.value) temporada.value = opcion.textContent;
        cargarCatalogo();
    });
    articulo.addEventListener('change', function () {
        var a = catalogo.articulos[Number(articulo.value)];
        if (a) aplicarArticulo(a);
    });
    todosLosColores.addEventListener('click', function () {
        if (!elegido) return;
        hermanos(elegido).forEach(rellenarLinea);
        todosLosColores.hidden = true;
    });
    document.getElementById('desdeCero').addEventListener('click', function () {
        [modelo, referencia, nombreBolso, codigoModelo, fotoId, fotoHojaId].forEach(function (c) { c.value = ''; });
        fotoSubida.value = '';
        lineas.innerHTML = '';
        anadirLinea();
        articulo.value = '';
        elegido = null;
        todosLosColores.hidden = true;
        pintarFoto();
        modelo.focus();
    });

    // --- Foto ---
    var fotoImg = document.getElementById('foto');
    var fotoVacia = document.getElementById('fotoVacia');
    var fotoVaciaTexto = document.getElementById('fotoVaciaTexto');
    var fotoSubida = document.getElementById('fotoSubida');
    var fotoId = document.getElementById('fotoId');
    var fotoHojaId = document.getElementById('fotoHojaId');
    var cambiarFoto = document.getElementById('cambiarFoto');
    var quitarFotoSubida = document.getElementById('quitarFotoSubida');
    var urlSubida = null;

    function pintarFoto() {
        var url = null;
        var subida = fotoSubida.files && fotoSubida.files[0];
        if (urlSubida) { URL.revokeObjectURL(urlSubida); urlSubida = null; }
        if (subida) {
            urlSubida = URL.createObjectURL(subida);
            url = urlSubida;
        } else if (fotoHojaId.value) {
            url = HC.urlFotoHoja + fotoHojaId.value + '/foto';
        } else if (fotoId.value) {
            url = HC.urlFotoCatalogo + fotoId.value;
        }
        fotoImg.hidden = !url;
        if (url) fotoImg.src = url; else fotoImg.removeAttribute('src');
        fotoVacia.hidden = !!url;
        fotoVaciaTexto.textContent = codigoModelo.value ? 'Este modelo no tiene foto en el catálogo.'
            : 'Elige el artículo para ver su foto, o sube una.';
        cambiarFoto.hidden = !url;
        quitarFotoSubida.hidden = !subida;
    }
    document.getElementById('subirFoto').addEventListener('click', function () { fotoSubida.click(); });
    cambiarFoto.addEventListener('click', function () { fotoSubida.click(); });
    fotoSubida.addEventListener('change', pintarFoto);
    quitarFotoSubida.addEventListener('click', function () {
        fotoSubida.value = '';
        pintarFoto();
    });

    // --- Generar ---
    function nombreDelFichero(cabecera) {
        var utf8 = /filename\*=UTF-8''([^;]+)/i.exec(cabecera || '');
        if (utf8) return decodeURIComponent(utf8[1]);
        var simple = /filename="?([^";]+)"?/i.exec(cabecera || '');
        return simple ? simple[1] : 'Hoja de Corte.xlsx';
    }

    function decir(texto, error) {
        resultado.className = 'alerta ' + (error ? 'error' : 'ok');
        resultado.textContent = texto;
    }

    form.addEventListener('submit', function (evento) {
        evento.preventDefault();
        // Las líneas se numeran seguidas: quitar una deja un hueco.
        todasLasLineas().forEach(function (linea, i) {
            linea.querySelectorAll('[name]').forEach(function (c) {
                c.name = c.name.replace(/^lineas\[\d+]/, 'lineas[' + i + ']');
            });
        });
        var boton = document.getElementById('botonGenerar');
        boton.disabled = true;
        var nombre = 'Hoja de Corte.xlsx';
        fetch(form.action, {method: 'POST', body: new FormData(form)})
            .then(function (r) {
                if (!r.ok) return r.text().then(function (t) { throw new Error(t || 'No se ha podido generar la hoja'); });
                nombre = nombreDelFichero(r.headers.get('Content-Disposition'));
                return r.blob();
            })
            .then(function (blob) {
                var enlace = document.createElement('a');
                enlace.href = URL.createObjectURL(blob);
                enlace.download = nombre;
                document.body.appendChild(enlace);
                enlace.click();
                enlace.remove();
                setTimeout(function () { URL.revokeObjectURL(enlace.href); }, 10000);
                decir('Hoja generada y guardada: ' + nombre, false);
            })
            .catch(function (error) { decir(error.message, true); })
            .then(function () { boton.disabled = false; });
    });

    // --- Arranque: con un cliente ya elegido (hoja importada), su catálogo ---
    rellenarTemporadas();
    if (cliente.value) cargarCatalogo();
    pintarFoto();
})();

// Zona de subida para cada <input type="file"> de la aplicación, con el
// aspecto de la de fotos de temporada (fragmento campoFotos): un recuadro
// punteado con su icono, "Suelta aquí el Archivo" y, con algo elegido, su
// nombre en lugar del texto.
//
// El input no se esconde: se queda ENCIMA de la zona, transparente, así que
// pinchar abre el selector y soltar un fichero lo recoge el propio navegador,
// con su evento change de siempre. Por eso los formularios y los scripts de
// cada pantalla no cambian; y un campo required sigue enseñando su aviso
// (escondido con display: none, el navegador no podría señalarlo y el
// formulario no se enviaría sin decir por qué).
//
// Los scripts de las pantallas vacían el campo con "campo.value = ''" (al
// elegir una temporada guardada, por ejemplo), y eso no lanza ningún evento:
// el setter de value del propio campo se envuelve para repintar el nombre.
//
// Se saltan los inputs con hidden: son los del campo de fotos, que ya trae
// su propia zona.
(function () {
    var ICONO = '<svg class="zona-icono" viewBox="0 0 24 24" fill="none" stroke="currentColor"'
        + ' stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round" aria-hidden="true">'
        + '<path d="M6.5 3h7.2L19 8.3v11.2a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19.5v-15A1.5 1.5 0 0 1 6.5 3z"/>'
        + '<path d="M13.5 3v5.5H19"/><path d="M12 17.5v-6M9.3 14.2 12 11.5l2.7 2.7"/></svg>';

    var setterValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
    var setterFiles = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'files');

    function iniciar(input) {
        if (input.hidden || input.closest('[data-campo-fotos], .zona-subida')) {
            return;
        }
        var zona = document.createElement('div');
        zona.className = 'zona-subida';
        zona.innerHTML = ICONO + '<span class="zona-titulo"></span>';
        input.parentNode.insertBefore(zona, input);
        zona.appendChild(input);
        var titulo = zona.querySelector('.zona-titulo');
        var invitacion = input.multiple ? 'Suelta aquí los Archivos' : 'Suelta aquí el Archivo';

        function pintar() {
            var ficheros = input.files ? Array.prototype.slice.call(input.files) : [];
            var nombres = ficheros.map(function (fichero) { return fichero.name; });
            titulo.textContent = nombres.length > 1
                ? nombres.length + ' archivos: ' + nombres.join(', ')
                : (nombres[0] || invitacion);
            zona.classList.toggle('con-fichero', nombres.length > 0);
        }

        Object.defineProperty(input, 'value', {
            configurable: true,
            get: function () { return setterValue.get.call(this); },
            set: function (valor) { setterValue.set.call(this, valor); pintar(); }
        });
        Object.defineProperty(input, 'files', {
            configurable: true,
            get: function () { return setterFiles.get.call(this); },
            set: function (lista) { setterFiles.set.call(this, lista); pintar(); }
        });

        input.addEventListener('change', pintar);
        input.addEventListener('dragenter', function () { zona.classList.add('encima'); });
        ['dragleave', 'drop'].forEach(function (tipo) {
            input.addEventListener(tipo, function () { zona.classList.remove('encima'); });
        });
        if (input.form) {
            // reset vacía los campos sin pasar por el setter de value.
            input.form.addEventListener('reset', function () { setTimeout(pintar); });
        }
        pintar();
    }

    function iniciarTodos() {
        document.querySelectorAll('input[type="file"]').forEach(iniciar);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciarTodos);
    } else {
        iniciarTodos();
    }
})();

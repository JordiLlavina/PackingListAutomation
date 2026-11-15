// Las casillas editables de la biblioteca de pieles (la tabla de pieles y la
// de proveedores): cada una se guarda sola al salir de ella, por fetch y sin
// recargar, para no perder lo tecleado en las demás. La casilla dice a dónde
// va (data-url) y qué dato es (data-campo); el servidor contesta el valor tal
// como ha quedado guardado ("1.1 - 1.3" vuelve "1,1-1,3") o, si no vale, un
// 400 con el motivo, que se enseña debajo de la casilla.
(function () {
    function estadoDe(campo) {
        return campo.parentElement.querySelector('.estado-celda');
    }

    function decir(campo, texto, clase) {
        var estado = estadoDe(campo);
        if (!estado) {
            return;
        }
        estado.textContent = texto;
        estado.className = 'estado-celda' + (clase ? ' ' + clase : '');
    }

    function guardar(campo) {
        if (campo.value === campo.dataset.guardado) {
            return;
        }
        decir(campo, 'Guardando…');
        var cuerpo = new URLSearchParams({campo: campo.dataset.campo, valor: campo.value});
        fetch(campo.dataset.url, {method: 'POST', body: cuerpo})
            .then(function (respuesta) {
                return respuesta.json().then(function (json) {
                    if (!respuesta.ok || !json.ok) {
                        throw new Error(json.mensaje || 'No se ha podido guardar');
                    }
                    return json;
                });
            })
            .then(function (json) {
                campo.value = json.valor;
                campo.dataset.guardado = campo.value;
                decir(campo, 'Guardado', 'guardado');
            })
            .catch(function (error) {
                // Un desplegable vuelve a lo guardado: se quedaría enseñando algo
                // que no es verdad. Una casilla de texto se deja como está, para
                // corregir lo tecleado sin volver a escribirlo.
                if (campo.tagName === 'SELECT') {
                    campo.value = campo.dataset.guardado;
                }
                decir(campo, error.message, 'error');
            });
    }

    document.querySelectorAll('.celda[data-url]').forEach(function (campo) {
        campo.dataset.guardado = campo.value;
        campo.addEventListener('change', function () { guardar(campo); });
        if (campo.tagName === 'INPUT') {
            campo.addEventListener('keydown', function (evento) {
                if (evento.key === 'Enter') {
                    evento.preventDefault();
                    campo.blur();
                }
            });
        }
    });
})();

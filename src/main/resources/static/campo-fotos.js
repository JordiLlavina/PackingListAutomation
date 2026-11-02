// Campo único de fotos de temporada (fragmento campoFotos): acepta la carpeta
// o su .zip, arrastrados o elegidos, y los manda con los mismos nombres de
// siempre —"carpeta" (un fichero por foto, con su ruta relativa como nombre)
// y "fotos" (el zip)—, así que el servidor no sabe ni le importa de qué
// manera se eligieron.
//
// Un solo <input type="file"> no puede ofrecer a la vez carpeta y fichero
// (webkitdirectory solo deja elegir carpetas), así que el campo lleva dos
// inputs escondidos y una zona: pinchar abre el de carpeta, el enlace abre el
// del zip, y soltar encima vale para las dos cosas. Lo soltado se mete en el
// input que toca con DataTransfer, y el formulario se envía normal.
(function () {
    function rutaDe(fichero) {
        return fichero.webkitRelativePath || fichero.name;
    }

    // Recorre una carpeta soltada. readEntries devuelve por tandas: hay que
    // llamarlo hasta que venga vacío o se pierden ficheros en carpetas grandes.
    function leerEntrada(entrada, ficheros) {
        if (entrada.isFile) {
            return new Promise(function (hecho, fallo) {
                entrada.file(function (fichero) {
                    // El nombre con la ruta es lo que le dice al servidor de qué modelo es.
                    ficheros.push(new File([fichero], entrada.fullPath.replace(/^\//, ''),
                        { type: fichero.type, lastModified: fichero.lastModified }));
                    hecho();
                }, fallo);
            });
        }
        const lector = entrada.createReader();
        return new Promise(function (hecho, fallo) {
            const hijos = [];
            (function tanda() {
                lector.readEntries(function (leidas) {
                    if (!leidas.length) {
                        Promise.all(hijos.map(function (hijo) { return leerEntrada(hijo, ficheros); }))
                            .then(hecho, fallo);
                        return;
                    }
                    Array.prototype.push.apply(hijos, leidas);
                    tanda();
                }, fallo);
            })();
        });
    }

    function iniciar(campo) {
        const carpeta = campo.querySelector('.cf-carpeta');
        const zip = campo.querySelector('.cf-zip');
        const zona = campo.querySelector('.zona-fotos');
        const elegido = campo.querySelector('.zona-elegido');
        const quitar = campo.querySelector('.cf-quitar');

        function pintar() {
            let texto = '';
            if (zip.files.length) {
                texto = 'Zip elegido: ' + zip.files[0].name;
            } else if (carpeta.files.length) {
                const primera = rutaDe(carpeta.files[0]);
                const raiz = primera.indexOf('/') > 0 ? primera.substring(0, primera.indexOf('/')) : '';
                texto = (raiz ? 'Carpeta ' + raiz + ': ' : '') + carpeta.files.length
                    + (carpeta.files.length === 1 ? ' fichero' : ' ficheros');
            }
            elegido.textContent = texto;
            campo.classList.toggle('con-fotos', !!texto);
            quitar.hidden = !texto;
        }

        function poner(input, ficheros) {
            const transferencia = new DataTransfer();
            ficheros.forEach(function (fichero) { transferencia.items.add(fichero); });
            input.files = transferencia.files;
        }

        function vaciar() {
            carpeta.value = '';
            zip.value = '';
            pintar();
        }

        zona.addEventListener('click', function (evento) {
            if (evento.target.closest('.cf-elegir-zip, .cf-quitar')) {
                return;
            }
            carpeta.click();
        });
        zona.addEventListener('keydown', function (evento) {
            if (evento.key === 'Enter' || evento.key === ' ') {
                evento.preventDefault();
                carpeta.click();
            }
        });
        campo.querySelector('.cf-elegir-zip').addEventListener('click', function () {
            zip.click();
        });
        quitar.addEventListener('click', vaciar);
        // Carpeta o zip, no los dos: elegir uno vacía el otro.
        carpeta.addEventListener('change', function () {
            if (carpeta.files.length) {
                zip.value = '';
            }
            pintar();
        });
        zip.addEventListener('change', function () {
            if (zip.files.length) {
                carpeta.value = '';
            }
            pintar();
        });

        ['dragenter', 'dragover'].forEach(function (tipo) {
            zona.addEventListener(tipo, function (evento) {
                evento.preventDefault();
                zona.classList.add('encima');
            });
        });
        ['dragleave', 'drop'].forEach(function (tipo) {
            zona.addEventListener(tipo, function () {
                zona.classList.remove('encima');
            });
        });
        zona.addEventListener('drop', function (evento) {
            evento.preventDefault();
            const items = Array.prototype.slice.call(evento.dataTransfer.items || []);
            const entradas = items.map(function (item) {
                return item.webkitGetAsEntry ? item.webkitGetAsEntry() : null;
            }).filter(Boolean);
            const sueltos = Array.prototype.slice.call(evento.dataTransfer.files || []);
            // Un .zip suelto va al campo del zip, tal cual.
            if (sueltos.length === 1 && /\.zip$/i.test(sueltos[0].name)
                    && !(entradas[0] && entradas[0].isDirectory)) {
                carpeta.value = '';
                poner(zip, sueltos);
                pintar();
                return;
            }
            if (!entradas.length) {
                return;
            }
            elegido.textContent = 'Leyendo la carpeta…';
            const ficheros = [];
            Promise.all(entradas.map(function (entrada) { return leerEntrada(entrada, ficheros); }))
                .then(function () {
                    zip.value = '';
                    poner(carpeta, ficheros);
                    pintar();
                }, function () {
                    elegido.textContent = 'No se ha podido leer la carpeta: elígela pinchando aquí.';
                });
        });
        pintar();
    }

    document.querySelectorAll('[data-campo-fotos]').forEach(iniciar);
})();

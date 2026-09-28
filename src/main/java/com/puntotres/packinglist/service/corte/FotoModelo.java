package com.puntotres.packinglist.service.corte;

import java.nio.file.Path;

/**
 * Una foto del zip, ya fuera de él: de qué modelo es, cómo se llamaba (para
 * ordenarla y enseñarla en el desplegable de foto principal) y dónde está la
 * copia, que lleva un nombre generado y no el del zip.
 */
public record FotoModelo(String modelo, String nombreOriginal, Path original) {
}

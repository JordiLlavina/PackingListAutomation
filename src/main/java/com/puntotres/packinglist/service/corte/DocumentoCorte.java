package com.puntotres.packinglist.service.corte;

import java.nio.file.Path;

/**
 * Un Word generado. Vive en disco, en el directorio de la sesión, y no en
 * memoria: una temporada son decenas de Word con fotos.
 */
public record DocumentoCorte(String descripcion, String nombreFichero, Path fichero) {
}

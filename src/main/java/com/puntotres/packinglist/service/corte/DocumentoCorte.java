package com.puntotres.packinglist.service.corte;

import java.nio.file.Path;

/**
 * Un Word generado. Vive en disco, en el directorio de la sesión, y no en
 * memoria: una temporada son decenas de Word con fotos. El tipo decide en
 * qué apartado de la pantalla de resultados sale.
 */
public record DocumentoCorte(Tipo tipo, String descripcion, String nombreFichero, Path fichero) {

    public enum Tipo { ORDENES, FOTOS }
}

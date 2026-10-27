package com.puntotres.packinglist.service.corte;

import java.nio.file.Path;
import java.util.List;

/**
 * Un documento generado: el Excel de órdenes de corte o el Word de fotos.
 * Vive en disco, en el directorio de la sesión, y no en memoria: una
 * temporada son decenas de fotos. El tipo decide en qué apartado de la
 * pantalla de resultados sale. {@code contenido} es el desglose que se
 * despliega en esa pantalla (en el Word de fotos, las fotos de cada modelo);
 * vacío en las órdenes.
 */
public record DocumentoCorte(Tipo tipo, String descripcion, String nombreFichero, Path fichero,
                             List<ContenidoModelo> contenido) {

    public enum Tipo { ORDENES, FOTOS }

    /** Un modelo del Word de fotos: su título, las referencias que lo comparten y cuántas fotos lleva. */
    public record ContenidoModelo(String titulo, List<String> referencias, int fotos) {

        public ContenidoModelo {
            referencias = List.copyOf(referencias);
        }
    }

    public DocumentoCorte {
        contenido = List.copyOf(contenido);
    }

    public DocumentoCorte(Tipo tipo, String descripcion, String nombreFichero, Path fichero) {
        this(tipo, descripcion, nombreFichero, fichero, List.of());
    }
}

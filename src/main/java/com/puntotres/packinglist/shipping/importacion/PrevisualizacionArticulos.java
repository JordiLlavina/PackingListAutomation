package com.puntotres.packinglist.shipping.importacion;

import java.util.List;

/** Vista previa de una importación de artículos, entre el "subir" y el "confirmar". */
public record PrevisualizacionArticulos(String nombreFichero, List<FilaArticulo> filas) {
    public List<FilaArticulo> validas() { return filas.stream().filter(FilaArticulo::valida).toList(); }
    public List<FilaArticulo> erroneas() { return filas.stream().filter(f -> !f.valida()).toList(); }
}

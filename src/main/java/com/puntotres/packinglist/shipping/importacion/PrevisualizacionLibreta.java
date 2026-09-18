package com.puntotres.packinglist.shipping.importacion;

import java.util.List;

/** Vista previa de una importación de la libreta de MyDHL+, antes de confirmarla. */
public record PrevisualizacionLibreta(String nombreFichero, List<FilaLibreta> filas) {
    public List<FilaLibreta> listas() { return filas.stream().filter(FilaLibreta::lista).toList(); }
    public List<FilaLibreta> duplicadas() { return filas.stream().filter(f -> f.valida() && f.duplicada()).toList(); }
    public List<FilaLibreta> erroneas() { return filas.stream().filter(f -> !f.valida()).toList(); }
}

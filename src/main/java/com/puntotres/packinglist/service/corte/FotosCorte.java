package com.puntotres.packinglist.service.corte;

import java.util.List;

/**
 * Las fotos de un modelo dentro del Word de fotos, con su nombre de bolso si
 * el pedido lo trae (vacío si no) y las referencias del pedido que comparten
 * esas fotos. Es uno por modelo y no por piel: la carpeta ULL027 vale para
 * ULL027.AL0103 y para ULL027.AL0216, y sin las pieles en el título las dos
 * serían las mismas hojas.
 */
public record FotosCorte(String modelo, String nombreBolso, List<String> referencias, List<Imagen> fotos) {

    public FotosCorte {
        nombreBolso = nombreBolso == null ? "" : nombreBolso.trim();
        referencias = List.copyOf(referencias);
        fotos = List.copyOf(fotos);
    }

    /** "F67043 - Sac Le Neige", o solo el modelo si el pedido no trae nombre. */
    public String titulo() {
        return nombreBolso.isEmpty() ? modelo : modelo + " - " + nombreBolso;
    }
}

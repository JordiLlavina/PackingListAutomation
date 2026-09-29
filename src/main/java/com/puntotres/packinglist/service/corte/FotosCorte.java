package com.puntotres.packinglist.service.corte;

import java.util.List;

/**
 * Un Word de fotos: las de un modelo, con su nombre de bolso si el pedido lo
 * trae (vacío si no). Es uno por modelo y no por piel: sin las pieles en la
 * cabecera, los de dos pieles del mismo modelo serían el mismo documento.
 */
public record FotosCorte(String modelo, String nombreBolso, List<Imagen> fotos) {

    public FotosCorte {
        nombreBolso = nombreBolso == null ? "" : nombreBolso.trim();
        fotos = List.copyOf(fotos);
    }

    /** "F67043 - Sac Le Neige", o solo el modelo si el pedido no trae nombre. */
    public String titulo() {
        return nombreBolso.isEmpty() ? modelo : modelo + " - " + nombreBolso;
    }
}

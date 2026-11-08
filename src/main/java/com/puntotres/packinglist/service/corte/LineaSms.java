package com.puntotres.packinglist.service.corte;

import java.util.List;

/**
 * Una fila del excel de SMS que se corta: una referencia ("PXCBC-F67008") en
 * un color del bolso, con sus pieles y cuántas muestras hay que cortar.
 * {@code hoja} y {@code fila} (la de Excel, contando desde 1) son para los
 * avisos. {@code croquis}: las imágenes de su celda CROQUIS, sin repetir; vacío
 * si no lleva (la hoja HOMME no tiene esa columna).
 */
public record LineaSms(String hoja, int fila, String referencia, String modelo, String nombreModelo,
                       ColorCorte color, PielesArticulo pieles, int muestras, List<Imagen> croquis) {

    public LineaSms {
        croquis = List.copyOf(croquis);
    }

    /** "FEMME, fila 5": dónde mirar en el excel. */
    public String donde() {
        return hoja + ", fila " + fila;
    }
}

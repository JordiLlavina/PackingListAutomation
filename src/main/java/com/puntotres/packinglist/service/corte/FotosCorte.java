package com.puntotres.packinglist.service.corte;

import java.util.List;

/** Un Word de fotos: las del modelo, con los datos de una de sus referencias (modelo + piel). */
public record FotosCorte(String temporada, String referencia, PielesArticulo pieles,
                         List<Imagen> fotos) {

    public FotosCorte {
        fotos = List.copyOf(fotos);
    }
}

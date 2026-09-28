package com.puntotres.packinglist.service.corte;

import java.util.List;

/** Lo que sale de generar: los documentos, en el orden en que se enseñan, y los avisos. */
public record ResultadoCorte(List<DocumentoCorte> documentos, List<String> avisos) {

    public ResultadoCorte {
        documentos = List.copyOf(documentos);
        avisos = List.copyOf(avisos);
    }
}

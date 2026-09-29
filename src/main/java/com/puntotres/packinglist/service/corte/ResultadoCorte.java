package com.puntotres.packinglist.service.corte;

import java.util.List;

/** Lo que sale de generar: los documentos, en el orden en que se enseñan, y los avisos. */
public record ResultadoCorte(List<DocumentoCorte> documentos, List<String> avisos) {

    public ResultadoCorte {
        documentos = List.copyOf(documentos);
        avisos = List.copyOf(avisos);
    }

    public List<DocumentoCorte> ordenes() {
        return delTipo(DocumentoCorte.Tipo.ORDENES);
    }

    public List<DocumentoCorte> fotos() {
        return delTipo(DocumentoCorte.Tipo.FOTOS);
    }

    private List<DocumentoCorte> delTipo(DocumentoCorte.Tipo tipo) {
        return documentos.stream().filter(documento -> documento.tipo() == tipo).toList();
    }
}

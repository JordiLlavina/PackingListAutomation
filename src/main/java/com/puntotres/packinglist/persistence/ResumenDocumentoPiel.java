package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

/** Un documento de una ficha de piel sin sus bytes, para listarlo. */
public record ResumenDocumentoPiel(Long id, Long fichaPielId, String nombreFichero, int tamano,
                                   LocalDateTime fecha) {
}

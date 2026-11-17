package com.puntotres.packinglist.persistence;

/** Una foto de una ficha de piel sin sus bytes, para pintar listas y galerías. */
public record ResumenFotoPiel(Long id, Long fichaPielId, String nombreFichero, boolean principal, int orden) {
}

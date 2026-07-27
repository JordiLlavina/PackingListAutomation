package com.puntotres.packinglist.service.etiquetasarticulo;

/**
 * Una hoja del excel de etiquetas: su nombre y la etiqueta que se repite en
 * ella.
 *
 * UNA etiqueta, no una lista: las 40 de la hoja son idénticas (una hoja =
 * una fila del pedido = un EAN13) y el builder es quien las repite en la
 * rejilla. El nombre llega ya saneado y recortado a 31 caracteres.
 */
public record HojaEtiquetas(String nombreHoja, EtiquetaArticulo etiqueta) {
}

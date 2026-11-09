package com.puntotres.packinglist.modelos.api.dto;

/**
 * Una imagen sacada de un PDF que podría ser una foto del modelo, a la espera
 * de que el despacho diga si lo es: una imagen incrustada en el PDF o una
 * página entera pintada (las fichas técnicas suelen ser dibujo vectorial, sin
 * ninguna imagen que extraer). Vive en un directorio temporal hasta que
 * caduca; la clave es su nombre dentro del lote.
 *
 * @param clave  cómo se pide ({@code imagen-1}, {@code pagina-2}): solo letras, cifras y guiones
 * @param ancho  en píxeles
 * @param alto   en píxeles
 * @param origen {@code "imagen"} o {@code "página N"}, para enseñarlo debajo
 */
public record CandidataImagen(String clave, int ancho, int alto, String origen) {
}

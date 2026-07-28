package com.puntotres.packinglist.service.escandallos;

/**
 * Un fichero de escandallo tal y como llega: su nombre y sus bytes.
 *
 * Existe para que el servicio no dependa de {@code MultipartFile}: el nombre
 * no es decorativo, es lo que identifica al escandallo en los avisos y lo que
 * nombra su hoja cuando el excel no trae MODEL.
 */
public record FicheroEscandallo(String nombre, byte[] contenido) {
}

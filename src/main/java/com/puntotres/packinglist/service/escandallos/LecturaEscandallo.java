package com.puntotres.packinglist.service.escandallos;

import java.util.List;

/**
 * Resultado de leer un fichero de escandallo: el escandallo y los avisos de lo
 * que no se ha podido leer del todo (un dato de cabecera que falta, una
 * cantidad ilegible).
 *
 * Los avisos no bloquean: el escandallo se devuelve igual y su hoja se genera
 * con los huecos en blanco, como el resto de la aplicación.
 */
public record LecturaEscandallo(Escandallo escandallo, List<String> avisos) {
}

package com.puntotres.packinglist.modelos.service.transcripcion;

import java.util.List;

/**
 * Lo que la IA saca de la transcripción de una nota de voz para el despacho.
 * Nunca lleva nulos: un texto que no hay es la cadena vacía y una lista que no
 * hay, la lista vacía ({@link ResumidorClaude#parsear}).
 *
 * @param titulo             una línea, para el título de un aviso que no lo tiene
 * @param resumen            unas frases con lo que se ha cambiado
 * @param tipoCambioSugerido una clave de {@code modelos.tipos-cambio}; vacía si no lo ve claro
 * @param materiales         los materiales que se nombran
 * @param dudas              lo que no se ha entendido al escucharla
 */
public record ResumenIa(String titulo, String resumen, String tipoCambioSugerido, List<String> materiales,
                        List<String> dudas) {
}

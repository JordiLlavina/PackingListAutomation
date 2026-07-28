package com.puntotres.packinglist.service.escandallos;

import java.util.List;

/**
 * El libro con todos los escandallos procesados: los bytes del {@code .xlsx},
 * los nombres de sus hojas (en el mismo orden que los ficheros subidos) y los
 * avisos de todo lo que no ha salido redondo.
 *
 * Lo devuelven tanto {@link EscandallosExcelBuilder} —con sus propios avisos,
 * los choques de nombre de hoja— como {@link EscandallosGenerationService},
 * que le suma los de la lectura de cada fichero. Es el mismo contrato en los
 * dos sitios: excel + qué hay dentro + qué mirar.
 *
 * {@code contenido} es {@code null} cuando no se ha podido procesar ningún
 * fichero: entonces no hay nada que descargar y solo quedan los avisos.
 */
public record ExcelEscandallos(byte[] contenido, List<String> hojas, List<String> avisos) {

    public boolean estaVacio() {
        return contenido == null || hojas.isEmpty();
    }
}

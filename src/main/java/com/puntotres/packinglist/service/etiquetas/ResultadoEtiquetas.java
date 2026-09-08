package com.puntotres.packinglist.service.etiquetas;

import java.util.ArrayList;
import java.util.List;

import com.puntotres.packinglist.service.ExcelGenerado;

/**
 * Salida de la generación de etiquetas: un excel por destinación soportada
 * y los avisos acumulados (destinaciones sin implementar, referencias que
 * no están en el excel de pedido...). Nunca se lanza excepción por datos
 * resolubles por un humano: se avisa y se genera lo que se pueda.
 *
 * Los avisos se guardan despiezados ({@link AvisoEtiqueta}) porque la
 * pantalla de resultados los agrupa, los compacta y resalta parte de ellos.
 * {@link #getAvisos()} sigue dando la lista de frases de siempre, ya montada
 * y de solo lectura: quien únicamente quiera leerlas no tiene que saber nada
 * de la estructura.
 */
public class ResultadoEtiquetas {

    private final List<ExcelGenerado> excels = new ArrayList<>();
    private final List<AvisoEtiqueta> avisos = new ArrayList<>();

    public List<ExcelGenerado> getExcels() { return excels; }

    /** Los avisos despiezados; es la lista que se rellena al generar. */
    public List<AvisoEtiqueta> getDetalle() { return avisos; }

    /** Las frases ya montadas. Vista derivada: añadir aquí no tendría efecto. */
    public List<String> getAvisos() {
        return avisos.stream().map(AvisoEtiqueta::texto).toList();
    }
}

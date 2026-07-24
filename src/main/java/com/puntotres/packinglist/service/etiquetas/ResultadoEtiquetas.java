package com.puntotres.packinglist.service.etiquetas;

import java.util.ArrayList;
import java.util.List;

import com.puntotres.packinglist.service.ExcelGenerado;

/**
 * Salida de la generación de etiquetas: un excel por destinación soportada
 * y los avisos acumulados (destinaciones sin implementar, referencias que
 * no están en el excel de pedido...). Nunca se lanza excepción por datos
 * resolubles por un humano: se avisa y se genera lo que se pueda.
 */
public class ResultadoEtiquetas {

    private final List<ExcelGenerado> excels = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();

    public List<ExcelGenerado> getExcels() { return excels; }
    public List<String> getAvisos() { return avisos; }
}

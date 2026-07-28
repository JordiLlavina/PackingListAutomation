package com.puntotres.packinglist.service.etiquetasarticulo;

import java.util.ArrayList;
import java.util.List;

/**
 * Salida de la generación de etiquetas de artículo: un excel por grupo
 * (tipo × país) con filas, más los avisos acumulados (filas del pedido sin
 * ARTICLE, EAN13 inválidos, tallas vacías...).
 *
 * Nunca se lanza excepción por datos resolubles por un humano: se avisa y se
 * genera lo que se pueda, igual que ResultadoEtiquetas.
 */
public class ResultadoEtiquetasArticulo {

    private final List<ExcelEtiquetasArticulo> excels = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();

    public List<ExcelEtiquetasArticulo> getExcels() {
        return excels;
    }

    public List<String> getAvisos() {
        return avisos;
    }
}

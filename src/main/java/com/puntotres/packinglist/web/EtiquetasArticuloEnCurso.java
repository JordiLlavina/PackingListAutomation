package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.service.etiquetasarticulo.ExcelEtiquetasArticulo;

/**
 * Estado de la generación de etiquetas de artículo entre la pantalla de
 * entrada y la de resultados.
 *
 * Independiente de EnvioEnCurso a propósito: son dos flujos que no comparten
 * nada y que pueden estar a medias a la vez en la misma sesión sin pisarse.
 */
@Component
@SessionScope
public class EtiquetasArticuloEnCurso {

    private String claveCliente;
    private final List<ExcelEtiquetasArticulo> excels = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();

    public boolean estaVacio() {
        return excels.isEmpty();
    }

    public void reiniciar() {
        claveCliente = null;
        excels.clear();
        avisos.clear();
    }

    public String getClaveCliente() { return claveCliente; }
    public void setClaveCliente(String claveCliente) { this.claveCliente = claveCliente; }

    public List<ExcelEtiquetasArticulo> getExcels() { return excels; }
    public List<String> getAvisos() { return avisos; }
}

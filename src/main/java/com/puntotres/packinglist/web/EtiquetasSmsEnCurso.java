package com.puntotres.packinglist.web;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.service.etiquetasarticulo.EtiquetasSmsService.ResultadoEtiquetasSms;

/**
 * Las etiquetas de SMS generadas en esta sesión, para la pantalla de
 * descarga. Solo las últimas: no se guarda nada más.
 */
@Component
@SessionScope
public class EtiquetasSmsEnCurso {

    private String cliente;
    private String temporada;
    private ResultadoEtiquetasSms resultado;

    public void guardar(String cliente, String temporada, ResultadoEtiquetasSms resultado) {
        this.cliente = cliente;
        this.temporada = temporada;
        this.resultado = resultado;
    }

    public String getCliente() {
        return cliente;
    }

    public String getTemporada() {
        return temporada;
    }

    public ResultadoEtiquetasSms getResultado() {
        return resultado;
    }
}

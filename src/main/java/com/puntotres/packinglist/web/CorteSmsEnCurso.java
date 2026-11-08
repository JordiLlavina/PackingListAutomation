package com.puntotres.packinglist.web;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.service.corte.CorteSmsService.ResultadoSms;

/**
 * Las hojas de corte de SMS generadas en esta sesión, para la pantalla de
 * descarga. Solo la última: no se guarda nada más.
 */
@Component
@SessionScope
public class CorteSmsEnCurso {

    private String cliente;
    private String temporada;
    private ResultadoSms resultado;

    public void guardar(String cliente, String temporada, ResultadoSms resultado) {
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

    public ResultadoSms getResultado() {
        return resultado;
    }
}

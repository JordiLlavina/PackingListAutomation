package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.List;

import com.puntotres.packinglist.model.EnvioInput;

/**
 * El packing regenerado: el mismo {@code EnvioInput} que produce cualquier
 * otra vía de entrada, más el resumen por destinación y lo que haya que
 * contarle al usuario.
 *
 * Que la salida sea un EnvioInput es lo que hace que de aquí en adelante no
 * haya nada especial: revisión, packing lists, etiquetas y volcado ERP no se
 * enteran de que estos datos vienen de un taller.
 */
public class ResultadoPackingTaller {

    private final EnvioInput envio;
    private final List<ResumenDestino> resumen = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();
    private final List<String> bloqueos = new ArrayList<>();

    public ResultadoPackingTaller(EnvioInput envio) {
        this.envio = envio;
    }

    public EnvioInput getEnvio() {
        return envio;
    }

    public List<ResumenDestino> getResumen() {
        return resumen;
    }

    public List<String> getAvisos() {
        return avisos;
    }

    public List<String> getBloqueos() {
        return bloqueos;
    }

    public boolean sePuedeGenerar() {
        return bloqueos.isEmpty();
    }
}

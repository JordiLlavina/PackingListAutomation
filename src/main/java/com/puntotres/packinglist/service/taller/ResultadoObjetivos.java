package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que el excel de pedido del cliente dice de cada fila del taller.
 *
 * Separa avisos de bloqueos porque no son lo mismo: una referencia que no
 * aparece en el pedido es un AVISO —el usuario teclea la cantidad y sigue—,
 * pero un pedido cuya destinación no se puede determinar es un BLOQUEO, y ahí
 * no se genera nada: un bulto en la destinación equivocada no lo arregla
 * después nadie.
 */
public class ResultadoObjetivos {

    private final Map<Integer, List<ObjetivoDestino>> porFila = new LinkedHashMap<>();
    private final List<String> avisos = new ArrayList<>();
    private final List<String> bloqueos = new ArrayList<>();

    /** Los objetivos de una fila del taller, identificada por su fila del excel. */
    public List<ObjetivoDestino> objetivosDe(LineaTaller linea) {
        return porFila.getOrDefault(linea.fila(), List.of());
    }

    public void anadir(LineaTaller linea, ObjetivoDestino objetivo) {
        porFila.computeIfAbsent(linea.fila(), fila -> new ArrayList<>()).add(objetivo);
    }

    public List<String> getAvisos() {
        return avisos;
    }

    public List<String> getBloqueos() {
        return bloqueos;
    }
}

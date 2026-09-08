package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /**
     * Las filas que el excel de pedido ha reconocido de verdad.
     *
     * Se lleva aparte y no se deduce de "tiene objetivos" porque una fila que
     * el pedido NO reconoce puede acabar teniendo objetivos igualmente: en AMI
     * se le rellena el PO de sus hermanas de referencia, con cantidad cero,
     * para que el usuario solo tenga que teclear cuánto va. Esa fila sigue
     * siendo una fila sin pedido y hay que seguir marcándola en pantalla.
     */
    private final Set<Integer> reconocidas = new LinkedHashSet<>();
    private final List<String> avisos = new ArrayList<>();
    private final List<String> bloqueos = new ArrayList<>();

    /** Los objetivos de una fila del taller, identificada por su fila del excel. */
    public List<ObjetivoDestino> objetivosDe(LineaTaller linea) {
        return porFila.getOrDefault(linea.fila(), List.of());
    }

    public void anadir(LineaTaller linea, ObjetivoDestino objetivo) {
        porFila.computeIfAbsent(linea.fila(), fila -> new ArrayList<>()).add(objetivo);
        reconocidas.add(linea.fila());
    }

    /**
     * Igual, pero sin dar la fila por reconocida: es una sugerencia que sale
     * de otra fila, no de una línea de pedido suya.
     */
    public void sugerir(LineaTaller linea, ObjetivoDestino objetivo) {
        porFila.computeIfAbsent(linea.fila(), fila -> new ArrayList<>()).add(objetivo);
    }

    /** Si el excel de pedido traía una línea para esta fila del taller. */
    public boolean estaEnElPedido(LineaTaller linea) {
        return reconocidas.contains(linea.fila());
    }

    public List<String> getAvisos() {
        return avisos;
    }

    public List<String> getBloqueos() {
        return bloqueos;
    }
}

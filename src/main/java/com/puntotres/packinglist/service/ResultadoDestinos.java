package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de resolver las destinaciones de un envío contra el catálogo del
 * cliente: la lista ya fusionada por destino padre y los avisos de lo que el
 * usuario tiene que mirar (números de caja o de palet repetidos entre hijas,
 * abreviatura sin configurar, fecha ilegible).
 */
public class ResultadoDestinos {

    private final List<EnvioImportado.DestinoImportado> destinos = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();

    public List<EnvioImportado.DestinoImportado> getDestinos() { return destinos; }
    public List<String> getAvisos() { return avisos; }
}

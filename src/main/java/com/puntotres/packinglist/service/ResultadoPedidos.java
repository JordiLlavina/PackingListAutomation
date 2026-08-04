package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de completar los números de pedido desde el excel del cliente:
 * solo avisos, porque el número completo se escribe sobre las propias
 * CajaData, igual que hace la asignación de palets.
 */
public class ResultadoPedidos {

    private final List<String> avisos = new ArrayList<>();

    public List<String> getAvisos() { return avisos; }
}

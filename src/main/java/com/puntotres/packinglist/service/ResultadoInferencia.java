package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de la inferencia de pesos: avisos de configuración que impiden
 * inferir (tamaños de caja sin tara en application.yml). Los pesos en sí se
 * escriben sobre las propias CajaData, igual que en ResultadoAsignacion.
 *
 * Que una referencia no tenga todavía ningún peso conocido NO es un aviso:
 * es el estado normal de partida y ya se ve en la pantalla de revisión como
 * filas pendientes. Aquí solo van los problemas que el usuario no podría
 * deducir mirando la tabla.
 */
public class ResultadoInferencia {

    private final List<String> avisos = new ArrayList<>();

    public List<String> getAvisos() { return avisos; }
}

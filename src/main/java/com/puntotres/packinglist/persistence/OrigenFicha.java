package com.puntotres.packinglist.persistence;

/**
 * De dónde salieron los datos de una {@link FichaPiel}: tecleados, leídos de
 * una ficha técnica o propuestos por un agente. Hoy todo entra a mano; los
 * otros dos están para que, cuando llegue el agente, se sepa qué fichas hay
 * que mirar con más cuidado.
 */
public enum OrigenFicha {
    MANUAL, FICHA, AGENTE
}

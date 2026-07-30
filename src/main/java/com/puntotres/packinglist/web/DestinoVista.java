package com.puntotres.packinglist.web;

import java.util.List;

/**
 * Una destinación en la pantalla de revisión: su índice en el envío (que
 * localiza sus cajas al aplicar los pesos), su nombre y sus filas ya
 * compactadas.
 *
 * {@code totalCajas} son los bultos reales de la destinación y NO coincide con
 * {@code filas.size()}: una fila puede representar un tramo compactado ("4-8").
 */
public record DestinoVista(int indice, String nombre, List<FilaCaja> filas, int totalCajas) {
}

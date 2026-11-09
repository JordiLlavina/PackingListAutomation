package com.puntotres.packinglist.modelos.persistence;

/**
 * En qué sentido va una modificación: un aviso del taller al despacho (un
 * cambio que el taller ha hecho o ha visto y el despacho tiene que aplicar) o
 * una orden del despacho al taller (algo que el taller tiene que hacer).
 */
public enum Direccion {
    TALLER_A_DESPACHO, DESPACHO_A_TALLER
}

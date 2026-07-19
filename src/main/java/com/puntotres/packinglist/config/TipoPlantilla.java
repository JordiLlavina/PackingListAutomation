package com.puntotres.packinglist.config;

/**
 * Tipo de plantilla Excel que usa un cliente. Determina qué generador
 * construye sus packing lists:
 *
 * <ul>
 *   <li>{@link #AMI}: plantilla de matriz de tallas de AMI (bolsos/cinturones),
 *       un excel por referencia + color.</li>
 *   <li>{@link #APC}: plantilla francesa de A.P.C., un excel por destino con
 *       las cajas agrupadas por palet.</li>
 *   <li>{@link #GENERIC}: plantilla estándar Puntotres, un excel por destino;
 *       vale para cualquier cliente nuevo sin plantilla propia.</li>
 * </ul>
 */
public enum TipoPlantilla {
    AMI,
    APC,
    GENERIC
}

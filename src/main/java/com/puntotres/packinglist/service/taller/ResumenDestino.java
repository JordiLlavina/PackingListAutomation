package com.puntotres.packinglist.service.taller;

/**
 * Una línea del resumen que se enseña antes de generar:
 *
 * <pre>
 * PARIS | 240 uds | 24 cajas | 2 palets | último palet 0,92 m de 1,68 m
 * </pre>
 *
 * Sirve para ver el efecto de un cambio de cartón o de unidades por caja sin
 * llegar hasta la pantalla de revisión y tener que volver.
 */
public record ResumenDestino(
        String destino,
        int unidades,
        int cajas,
        int palets,
        int alturaUltimoPaletCm,
        int alturaUtilCm) {
}

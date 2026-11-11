package com.puntotres.packinglist.modelos.service;

import com.puntotres.packinglist.modelos.api.dto.Fusion;

/**
 * La referencia que se quiere asignar ya la tiene otro modelo del cliente
 * (casi siempre el que ha entrado por el pedido): asignarla es juntar los dos,
 * y eso no se hace sin confirmar. Lleva lo que se juntaría para que la
 * pantalla lo diga; la API lo devuelve como un 409.
 */
public class FusionPendiente extends RuntimeException {

    private final transient Fusion fusion;

    public FusionPendiente(String mensaje, Fusion fusion) {
        super(mensaje);
        this.fusion = fusion;
    }

    public Fusion fusion() {
        return fusion;
    }
}

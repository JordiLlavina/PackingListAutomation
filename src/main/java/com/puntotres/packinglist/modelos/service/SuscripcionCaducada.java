package com.puntotres.packinglist.modelos.service;

import java.io.IOException;

/**
 * El servidor de push contesta que esa suscripción ya no existe (404 o 410):
 * el navegador la ha tirado (se desinstaló la app, se quitó el permiso) y no
 * va a volver. {@link Push} la borra.
 */
public class SuscripcionCaducada extends IOException {

    public SuscripcionCaducada(String mensaje) {
        super(mensaje);
    }
}

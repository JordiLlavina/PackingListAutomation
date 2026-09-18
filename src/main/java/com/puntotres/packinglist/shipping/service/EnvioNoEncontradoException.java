package com.puntotres.packinglist.shipping.service;

/** No existe ningún envío con ese id. */
public class EnvioNoEncontradoException extends RuntimeException {
    public EnvioNoEncontradoException(Long id) { super("No existe el envío " + id); }
}

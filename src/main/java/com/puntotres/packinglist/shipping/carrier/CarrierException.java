package com.puntotres.packinglist.shipping.carrier;

/**
 * Fallo hablando con un transportista, ya traducido a un texto que se puede
 * enseñar al usuario (sin URLs internas ni trazas). La causa técnica va en
 * getCause() para el log.
 */
public class CarrierException extends RuntimeException {

    private final String mensajeUsuario;

    public CarrierException(String mensajeUsuario) { this(mensajeUsuario, null); }

    public CarrierException(String mensajeUsuario, Throwable causa) {
        super(mensajeUsuario, causa);
        this.mensajeUsuario = mensajeUsuario;
    }

    public String getMensajeUsuario() { return mensajeUsuario; }
}

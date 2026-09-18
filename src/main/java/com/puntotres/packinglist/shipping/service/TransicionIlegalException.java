package com.puntotres.packinglist.shipping.service;

/** Una transición que la máquina de estados no permite. Es un fallo de programa o una carrera, no un dato mal tecleado. */
public class TransicionIlegalException extends RuntimeException {
    public TransicionIlegalException(String mensaje) { super(mensaje); }
}

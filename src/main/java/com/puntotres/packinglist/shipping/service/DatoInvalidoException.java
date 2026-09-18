package com.puntotres.packinglist.shipping.service;

/** Un dato de mantenimiento que no se puede guardar (número repetido, campo vacío). El mensaje es para el usuario. */
public class DatoInvalidoException extends RuntimeException {
    public DatoInvalidoException(String mensaje) { super(mensaje); }
}

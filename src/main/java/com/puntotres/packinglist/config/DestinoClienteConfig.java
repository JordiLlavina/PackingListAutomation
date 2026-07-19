package com.puntotres.packinglist.config;

/**
 * Datos fijos de un destino de un cliente: el nombre legal del receptor y
 * la dirección de entrega que van en la cabecera del packing list.
 * Solo los usan las plantillas que imprimen dirección por destino (APC).
 */
public class DestinoClienteConfig {

    private String nombreCliente;
    private String direccion;

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }
}

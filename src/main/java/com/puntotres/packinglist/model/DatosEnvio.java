package com.puntotres.packinglist.model;

/**
 * Datos de cabecera del excel que NO salen de las imágenes: los introduce
 * el usuario (futura pantalla de revisión) y se aplican a todos los
 * packing lists generados de la destinación.
 *
 * Las fechas van como String en formato dd/MM/yyyy, igual que en el
 * resto del proyecto.
 */
public class DatosEnvio {

    private String temporada;
    private String numeroFactura;
    private String fechaFactura;
    private String fechaEnvio;
    // Cliente seleccionado en la pantalla de entrada (clave del catálogo
    // de ClientesProperties) y ciudad/país del proveedor, editables solo
    // para clientes AMI (por defecto BADALONA/SPAIN).
    private String claveCliente;
    private String ciudadProveedor;
    private String paisProveedor;

    public String getTemporada() { return temporada; }
    public void setTemporada(String temporada) { this.temporada = temporada; }

    public String getClaveCliente() { return claveCliente; }
    public void setClaveCliente(String claveCliente) { this.claveCliente = claveCliente; }

    public String getCiudadProveedor() { return ciudadProveedor; }
    public void setCiudadProveedor(String ciudadProveedor) { this.ciudadProveedor = ciudadProveedor; }

    public String getPaisProveedor() { return paisProveedor; }
    public void setPaisProveedor(String paisProveedor) { this.paisProveedor = paisProveedor; }

    public String getNumeroFactura() { return numeroFactura; }
    public void setNumeroFactura(String numeroFactura) { this.numeroFactura = numeroFactura; }

    public String getFechaFactura() { return fechaFactura; }
    public void setFechaFactura(String fechaFactura) { this.fechaFactura = fechaFactura; }

    public String getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(String fechaEnvio) { this.fechaEnvio = fechaEnvio; }
}

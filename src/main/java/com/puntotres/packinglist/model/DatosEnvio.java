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

    public String getTemporada() { return temporada; }
    public void setTemporada(String temporada) { this.temporada = temporada; }

    public String getNumeroFactura() { return numeroFactura; }
    public void setNumeroFactura(String numeroFactura) { this.numeroFactura = numeroFactura; }

    public String getFechaFactura() { return fechaFactura; }
    public void setFechaFactura(String fechaFactura) { this.fechaFactura = fechaFactura; }

    public String getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(String fechaEnvio) { this.fechaEnvio = fechaEnvio; }
}

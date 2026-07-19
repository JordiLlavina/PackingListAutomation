package com.puntotres.packinglist.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Formulario de la pantalla de entrada: el JSON del envío pegado a mano
 * más la cabecera que no sale de las imágenes ({@code DatosEnvio}).
 *
 * Las fechas se validan aquí contra el formato dd/MM/yyyy que espera
 * {@code AmiExcelBuilder.escribirFecha}: es mejor un error de formulario
 * que un fallo al generar el excel.
 */
public class EnvioForm {

    private static final String FORMATO_FECHA = "\\d{2}/\\d{2}/\\d{4}";

    @NotBlank(message = "Elige un cliente")
    private String cliente;

    @NotBlank(message = "Pega el JSON del envío")
    private String json;

    @NotBlank(message = "La temporada es obligatoria")
    private String temporada;

    // Solo para clientes AMI: ciudad/país del proveedor en la cabecera del
    // excel. Opcionales (por defecto BADALONA/SPAIN, que vale casi siempre).
    private String ciudadProveedor;
    private String paisProveedor;

    @NotBlank(message = "El número de factura es obligatorio")
    private String numeroFactura;

    @NotBlank(message = "La fecha de factura es obligatoria")
    @Pattern(regexp = FORMATO_FECHA, message = "Formato de fecha: dd/MM/yyyy")
    private String fechaFactura;

    @NotBlank(message = "La fecha de envío es obligatoria")
    @Pattern(regexp = FORMATO_FECHA, message = "Formato de fecha: dd/MM/yyyy")
    private String fechaEnvio;

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public String getJson() { return json; }
    public void setJson(String json) { this.json = json; }

    public String getCiudadProveedor() { return ciudadProveedor; }
    public void setCiudadProveedor(String ciudadProveedor) { this.ciudadProveedor = ciudadProveedor; }

    public String getPaisProveedor() { return paisProveedor; }
    public void setPaisProveedor(String paisProveedor) { this.paisProveedor = paisProveedor; }

    public String getTemporada() { return temporada; }
    public void setTemporada(String temporada) { this.temporada = temporada; }

    public String getNumeroFactura() { return numeroFactura; }
    public void setNumeroFactura(String numeroFactura) { this.numeroFactura = numeroFactura; }

    public String getFechaFactura() { return fechaFactura; }
    public void setFechaFactura(String fechaFactura) { this.fechaFactura = fechaFactura; }

    public String getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(String fechaEnvio) { this.fechaEnvio = fechaEnvio; }
}

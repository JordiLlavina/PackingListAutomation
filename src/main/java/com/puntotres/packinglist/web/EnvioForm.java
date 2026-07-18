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

    @NotBlank(message = "Pega el JSON del envío")
    private String json;

    @NotBlank(message = "La temporada es obligatoria")
    private String temporada;

    @NotBlank(message = "El número de factura es obligatorio")
    private String numeroFactura;

    @NotBlank(message = "La fecha de factura es obligatoria")
    @Pattern(regexp = FORMATO_FECHA, message = "Formato de fecha: dd/MM/yyyy")
    private String fechaFactura;

    @NotBlank(message = "La fecha de envío es obligatoria")
    @Pattern(regexp = FORMATO_FECHA, message = "Formato de fecha: dd/MM/yyyy")
    private String fechaEnvio;

    public String getJson() { return json; }
    public void setJson(String json) { this.json = json; }

    public String getTemporada() { return temporada; }
    public void setTemporada(String temporada) { this.temporada = temporada; }

    public String getNumeroFactura() { return numeroFactura; }
    public void setNumeroFactura(String numeroFactura) { this.numeroFactura = numeroFactura; }

    public String getFechaFactura() { return fechaFactura; }
    public void setFechaFactura(String fechaFactura) { this.fechaFactura = fechaFactura; }

    public String getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(String fechaEnvio) { this.fechaEnvio = fechaEnvio; }
}

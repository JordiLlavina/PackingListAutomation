package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Formulario de la pantalla de entrada: los datos del envío (JSON pegado a
 * mano o imágenes para el modo CLAUDE, según {@code modo}) más la cabecera
 * que no sale de las imágenes ({@code DatosEnvio}).
 *
 * El JSON y las imágenes se validan en el controlador según el modo activo
 * (solo uno de los dos es obligatorio); por eso no llevan @NotBlank aquí.
 *
 * Las fechas se validan aquí contra el formato dd/MM/yyyy que espera
 * {@code AmiExcelBuilder.escribirFecha}: es mejor un error de formulario
 * que un fallo al generar el excel.
 */
public class EnvioForm {

    private static final String FORMATO_FECHA = "\\d{2}/\\d{2}/\\d{4}";

    /** Modo de entrada activo: JSON (por defecto), CLAUDE o FORMULARIO. */
    private String modo = "JSON";

    @NotBlank(message = "Elige un cliente")
    private String cliente;

    private String json;

    /** Fotos del packing list para el modo CLAUDE. */
    private List<MultipartFile> imagenes = new ArrayList<>();

    /**
     * Excel de pedido de la temporada del cliente. Opcional: APC lo usa para
     * completar el nº de pedido y AMI para no volver a pedirlo en el Paso 2
     * de etiquetas. Solo se muestra a los clientes que lo declaran.
     */
    private MultipartFile pedidoCliente;

    @NotBlank(message = "La temporada es obligatoria")
    private String temporada;

    // Solo para clientes AMI: ciudad/país del proveedor en la cabecera del
    // excel. Opcionales (por defecto BADALONA/SPAIN, que vale casi siempre).
    private String ciudadProveedor;
    private String paisProveedor;

    @NotBlank(message = "El número de factura es obligatorio")
    private String numeroFactura;

    // Nº de comanda de ICSuite para el volcado ERP. Opcional a propósito:
    // un envío se puede generar sin él y rellenar la columna a mano después.
    private String numeroComanda;

    @NotBlank(message = "La fecha de factura es obligatoria")
    @Pattern(regexp = FORMATO_FECHA, message = "Formato de fecha: dd/MM/yyyy")
    private String fechaFactura;

    @NotBlank(message = "La fecha de envío es obligatoria")
    @Pattern(regexp = FORMATO_FECHA, message = "Formato de fecha: dd/MM/yyyy")
    private String fechaEnvio;

    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public List<MultipartFile> getImagenes() { return imagenes; }
    public void setImagenes(List<MultipartFile> imagenes) { this.imagenes = imagenes; }

    public String getJson() { return json; }
    public void setJson(String json) { this.json = json; }

    public MultipartFile getPedidoCliente() { return pedidoCliente; }
    public void setPedidoCliente(MultipartFile pedidoCliente) { this.pedidoCliente = pedidoCliente; }

    public String getCiudadProveedor() { return ciudadProveedor; }
    public void setCiudadProveedor(String ciudadProveedor) { this.ciudadProveedor = ciudadProveedor; }

    public String getPaisProveedor() { return paisProveedor; }
    public void setPaisProveedor(String paisProveedor) { this.paisProveedor = paisProveedor; }

    public String getTemporada() { return temporada; }
    public void setTemporada(String temporada) { this.temporada = temporada; }

    public String getNumeroFactura() { return numeroFactura; }
    public void setNumeroFactura(String numeroFactura) { this.numeroFactura = numeroFactura; }

    public String getNumeroComanda() { return numeroComanda; }
    public void setNumeroComanda(String numeroComanda) { this.numeroComanda = numeroComanda; }

    public String getFechaFactura() { return fechaFactura; }
    public void setFechaFactura(String fechaFactura) { this.fechaFactura = fechaFactura; }

    public String getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(String fechaEnvio) { this.fechaEnvio = fechaEnvio; }
}

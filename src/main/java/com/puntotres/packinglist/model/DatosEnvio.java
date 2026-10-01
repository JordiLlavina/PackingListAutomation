package com.puntotres.packinglist.model;

/**
 * Datos de cabecera del excel que NO salen de las imágenes: los introduce
 * el usuario en la pantalla de entrada y se aplican a todos los packing
 * lists generados del envío. La factura es la excepción: aquí solo es el
 * valor de partida, porque cada destinación lleva la suya (ver
 * {@link #facturaPara}).
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
    // Nº de comanda de ICSuite: no sale de las imágenes ni del JSON, lo
    // teclea el usuario y solo lo usa el volcado ERP. Opcional: si viene
    // vacío, la columna "Comanda" del volcado sale en blanco.
    private String numeroComanda;

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

    /**
     * La factura del packing list de una destinación: la que se tecleó en su
     * cabecera de la revisión o, si no se tecleó ninguna, la del envío.
     *
     * Cada packing list lleva su propia factura, así que la del envío (la de
     * la pantalla de entrada, que ya no es obligatoria) es solo el valor de
     * partida de todas las destinaciones. null o en blanco cuando no hay
     * ninguna de las dos: el excel sale con la celda de factura vacía y quien
     * genera avisa, en vez de inventar un número que va a un documento del
     * cliente.
     */
    public String facturaPara(DestinoData destino) {
        String propia = destino == null ? null : destino.getNumeroFactura();
        return propia != null && !propia.isBlank() ? propia.trim() : numeroFactura;
    }

    public String getFechaFactura() { return fechaFactura; }
    public void setFechaFactura(String fechaFactura) { this.fechaFactura = fechaFactura; }

    public String getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(String fechaEnvio) { this.fechaEnvio = fechaEnvio; }

    public String getNumeroComanda() { return numeroComanda; }
    public void setNumeroComanda(String numeroComanda) { this.numeroComanda = numeroComanda; }
}

package com.puntotres.packinglist.model;

import java.util.List;

/**
 * Todas las cajas de una destinación (resultado de combinar en un único
 * JSON las imágenes de detalle de esa destinación). La numeración de caja
 * es continua dentro de la destinación aunque haya varios pedidos.
 *
 * <p>{@code numeroFactura} es la factura de ESTA destinación: cada packing
 * list lleva la suya y se teclea en la cabecera de la destinación en la
 * pantalla de revisión. null = no se ha tecleado ninguna, y entonces manda
 * la del envío ({@link DatosEnvio#facturaPara}).
 */
public class DestinoData {

    private String nombreDestino;
    private List<CajaData> cajas;
    private String numeroFactura;

    public String getNombreDestino() { return nombreDestino; }
    public void setNombreDestino(String nombreDestino) { this.nombreDestino = nombreDestino; }

    public List<CajaData> getCajas() { return cajas; }
    public void setCajas(List<CajaData> cajas) { this.cajas = cajas; }

    public String getNumeroFactura() { return numeroFactura; }
    public void setNumeroFactura(String numeroFactura) { this.numeroFactura = numeroFactura; }
}

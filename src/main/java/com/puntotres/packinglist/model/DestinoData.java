package com.puntotres.packinglist.model;

import java.util.List;

/**
 * Todas las cajas de una destinación (resultado de combinar en un único
 * JSON las imágenes de detalle de esa destinación). La numeración de caja
 * es continua dentro de la destinación aunque haya varios pedidos.
 */
public class DestinoData {

    private String nombreDestino;
    private List<CajaData> cajas;

    public String getNombreDestino() { return nombreDestino; }
    public void setNombreDestino(String nombreDestino) { this.nombreDestino = nombreDestino; }

    public List<CajaData> getCajas() { return cajas; }
    public void setCajas(List<CajaData> cajas) { this.cajas = cajas; }
}

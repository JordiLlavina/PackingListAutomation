package com.puntotres.packinglist.model;

import java.util.List;

public class VoltadoErpData {
    private List<VoltadoErpLinea> lineas;
    private int totalLineas;
    private String nombreFichero;  // Volcado_ERP_<factura>.xlsx

    public VoltadoErpData(List<VoltadoErpLinea> lineas, String nombreFichero) {
        this.lineas = lineas;
        this.totalLineas = lineas.size();
        this.nombreFichero = nombreFichero;
    }

    // Getters
    public List<VoltadoErpLinea> getLineas() { return lineas; }
    public int getTotalLineas() { return totalLineas; }
    public String getNombreFichero() { return nombreFichero; }
    public boolean tieneDatos() { return totalLineas > 0; }
}

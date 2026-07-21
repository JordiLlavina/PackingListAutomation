package com.puntotres.packinglist.model;

import java.util.List;

public class VolcadoErpData {
    private List<VolcadoErpLinea> lineas;
    private int totalLineas;
    private String nombreFichero;  // Volcado_ICSUITE_<factura>.xlsx

    public VolcadoErpData(List<VolcadoErpLinea> lineas, String nombreFichero) {
        this.lineas = lineas;
        this.totalLineas = lineas.size();
        this.nombreFichero = nombreFichero;
    }

    // Getters
    public List<VolcadoErpLinea> getLineas() { return lineas; }
    public int getTotalLineas() { return totalLineas; }
    public String getNombreFichero() { return nombreFichero; }
    public boolean tieneDatos() { return totalLineas > 0; }
}

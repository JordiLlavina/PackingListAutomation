package com.puntotres.packinglist.service;

import java.util.List;

import com.puntotres.packinglist.model.CajaData;

/**
 * Un packing list generado (destinación + referencia + color): los bytes
 * del .xlsx y, si las hay, las cajas con pesos sin resolver. El excel se
 * genera igualmente con esas celdas de peso vacías; cajasPendientes existe
 * para que la pantalla de revisión pueda remarcarlas.
 */
public class ExcelGenerado {

    private final String referencia;
    private final String color;
    private final String nombreFichero;
    private final byte[] contenido;
    private final List<CajaData> cajasPendientes;

    public ExcelGenerado(String referencia, String color, String nombreFichero,
                         byte[] contenido, List<CajaData> cajasPendientes) {
        this.referencia = referencia;
        this.color = color;
        this.nombreFichero = nombreFichero;
        this.contenido = contenido;
        this.cajasPendientes = cajasPendientes;
    }

    public String getReferencia() { return referencia; }
    public String getColor() { return color; }
    public String getNombreFichero() { return nombreFichero; }
    public byte[] getContenido() { return contenido; }
    public List<CajaData> getCajasPendientes() { return cajasPendientes; }

    public boolean tienePesosPendientes() {
        return !cajasPendientes.isEmpty();
    }
}

package com.puntotres.packinglist.service;

import java.util.List;

import com.puntotres.packinglist.model.CajaData;

/**
 * Un packing list generado: los bytes del .xlsx y, si las hay, las cajas
 * con pesos sin resolver. El excel se genera igualmente con esas celdas de
 * peso vacías; cajasPendientes existe para que la pantalla de revisión
 * pueda remarcarlas.
 *
 * descripcion identifica el contenido del fichero en pantalla; su forma
 * depende del generador: AMI describe "destino · referencia color" (un
 * excel por referencia+color), APC/genérica describen solo el destino
 * (un excel por destino).
 */
public class ExcelGenerado {

    private final String descripcion;
    private final String nombreFichero;
    private final byte[] contenido;
    private final List<CajaData> cajasPendientes;

    public ExcelGenerado(String descripcion, String nombreFichero,
                         byte[] contenido, List<CajaData> cajasPendientes) {
        this.descripcion = descripcion;
        this.nombreFichero = nombreFichero;
        this.contenido = contenido;
        this.cajasPendientes = cajasPendientes;
    }

    public String getDescripcion() { return descripcion; }
    public String getNombreFichero() { return nombreFichero; }
    public byte[] getContenido() { return contenido; }
    public List<CajaData> getCajasPendientes() { return cajasPendientes; }

    public boolean tienePesosPendientes() {
        return !cajasPendientes.isEmpty();
    }
}

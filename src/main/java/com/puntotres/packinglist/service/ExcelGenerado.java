package com.puntotres.packinglist.service;

import java.util.List;

import com.puntotres.packinglist.model.CajaData;

/**
 * Un packing list generado: los bytes del .xlsx y, si las hay, las cajas
 * con pesos sin resolver. El excel se genera igualmente con esas celdas de
 * peso vacías; cajasPendientes existe para que la pantalla de revisión
 * pueda remarcarlas.
 *
 * El contenido se identifica en pantalla con destino y, si el generador
 * trocea por referencia+color (AMI), también referencia y color; APC y la
 * genérica (un excel por destino) los dejan a null. descripcion se deriva
 * de esos campos para quien quiera el texto plano.
 */
public class ExcelGenerado {

    private final String destino;
    private final String referencia;
    private final String color;
    private final String nombreFichero;
    private final byte[] contenido;
    private final List<CajaData> cajasPendientes;

    /** Excel por destino (APC, genérica): sin referencia ni color. */
    public ExcelGenerado(String destino, String nombreFichero,
                         byte[] contenido, List<CajaData> cajasPendientes) {
        this(destino, null, null, nombreFichero, contenido, cajasPendientes);
    }

    /** Excel por referencia+color (AMI). */
    public ExcelGenerado(String destino, String referencia, String color,
                         String nombreFichero, byte[] contenido,
                         List<CajaData> cajasPendientes) {
        this.destino = destino;
        this.referencia = referencia;
        this.color = color;
        this.nombreFichero = nombreFichero;
        this.contenido = contenido;
        this.cajasPendientes = cajasPendientes;
    }

    public String getDestino() { return destino; }
    public String getReferencia() { return referencia; }
    public String getColor() { return color; }

    /** Texto plano equivalente: "destino" o "destino · referencia color". */
    public String getDescripcion() {
        if (referencia == null) {
            return destino;
        }
        return destino + " · " + referencia + " " + color;
    }

    public String getNombreFichero() { return nombreFichero; }
    public byte[] getContenido() { return contenido; }
    public List<CajaData> getCajasPendientes() { return cajasPendientes; }

    public boolean tienePesosPendientes() {
        return !cajasPendientes.isEmpty();
    }
}

package com.puntotres.packinglist.model;

/**
 * Un palet de la imagen de distribución de palets del envío.
 * Cada palet contiene un rango contiguo de números de caja
 * (cajaInicio..cajaFin, ambos incluidos) de una destinación.
 */
public class PaletData {

    private String destino;
    private int numeroPalet;
    private int cajaInicio;
    private int cajaFin;

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public int getNumeroPalet() { return numeroPalet; }
    public void setNumeroPalet(int numeroPalet) { this.numeroPalet = numeroPalet; }

    public int getCajaInicio() { return cajaInicio; }
    public void setCajaInicio(int cajaInicio) { this.cajaInicio = cajaInicio; }

    public int getCajaFin() { return cajaFin; }
    public void setCajaFin(int cajaFin) { this.cajaFin = cajaFin; }

    public boolean contiene(int numeroCaja) {
        return numeroCaja >= cajaInicio && numeroCaja <= cajaFin;
    }
}

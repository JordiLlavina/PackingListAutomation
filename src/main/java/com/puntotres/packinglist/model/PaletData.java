package com.puntotres.packinglist.model;

/**
 * Un palet de la imagen de distribución de palets del envío.
 * Cada palet contiene un rango contiguo de números de caja
 * (cajaInicio..cajaFin, ambos incluidos) de una destinación.
 */
public class PaletData {

    /**
     * Lo que pesa un palet vacío cuando nadie ha dicho otra cosa. Cada palet
     * pesa distinto y se teclea en la columna "Palet (Kg)" de la revisión;
     * este es el valor con el que cuentan el packing list y las etiquetas
     * mientras tanto, y el que la pantalla enseña como sugerencia en gris.
     */
    public static final double TARA_DEFECTO_KG = 10.0;

    private String destino;
    private int numeroPalet;
    private int cajaInicio;
    private int cajaFin;
    // Opcionales: dimensiones "LxWxH" en cm y tara en kg; null si el JSON
    // no los trae y nadie la ha tecleado en la revisión (entonces se cuenta
    // TARA_DEFECTO_KG, ver taraOPorDefecto).
    private String medidas;
    private Double tara;

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public int getNumeroPalet() { return numeroPalet; }
    public void setNumeroPalet(int numeroPalet) { this.numeroPalet = numeroPalet; }

    public int getCajaInicio() { return cajaInicio; }
    public void setCajaInicio(int cajaInicio) { this.cajaInicio = cajaInicio; }

    public int getCajaFin() { return cajaFin; }
    public void setCajaFin(int cajaFin) { this.cajaFin = cajaFin; }

    public String getMedidas() { return medidas; }
    public void setMedidas(String medidas) { this.medidas = medidas; }

    public Double getTara() { return tara; }
    public void setTara(Double tara) { this.tara = tara; }

    /** La tara del palet, o {@link #TARA_DEFECTO_KG} si no se ha tecleado. */
    public double taraOPorDefecto() {
        return tara != null ? tara : TARA_DEFECTO_KG;
    }

    public boolean contiene(int numeroCaja) {
        return numeroCaja >= cajaInicio && numeroCaja <= cajaFin;
    }
}

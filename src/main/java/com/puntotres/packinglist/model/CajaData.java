package com.puntotres.packinglist.model;

/**
 * Una caja tal como sale de las imágenes de detalle de una destinación.
 *
 * Los pesos son Double (no double) porque pueden faltar en la imagen:
 * null significa "desconocido" y es el {@code WeightInferenceService}
 * quien intenta calcularlos. numeroPalet tampoco viene de la imagen:
 * lo rellena el {@code PaletAssignmentService} (null = sin palet asignado).
 */
public class CajaData {

    private int numeroCaja;
    private String numeroPedido;
    private String referencia;
    private String codigoColor;
    private String tamanoCaja;
    private int cantidad;
    private Double pesoNetoKg;
    private Double pesoBrutoKg;
    private Integer numeroPalet;

    public int getNumeroCaja() { return numeroCaja; }
    public void setNumeroCaja(int numeroCaja) { this.numeroCaja = numeroCaja; }

    public String getNumeroPedido() { return numeroPedido; }
    public void setNumeroPedido(String numeroPedido) { this.numeroPedido = numeroPedido; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getCodigoColor() { return codigoColor; }
    public void setCodigoColor(String codigoColor) { this.codigoColor = codigoColor; }

    public String getTamanoCaja() { return tamanoCaja; }
    public void setTamanoCaja(String tamanoCaja) { this.tamanoCaja = tamanoCaja; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public Double getPesoNetoKg() { return pesoNetoKg; }
    public void setPesoNetoKg(Double pesoNetoKg) { this.pesoNetoKg = pesoNetoKg; }

    public Double getPesoBrutoKg() { return pesoBrutoKg; }
    public void setPesoBrutoKg(Double pesoBrutoKg) { this.pesoBrutoKg = pesoBrutoKg; }

    public Integer getNumeroPalet() { return numeroPalet; }
    public void setNumeroPalet(Integer numeroPalet) { this.numeroPalet = numeroPalet; }

    public boolean tienePesosCompletos() {
        return pesoNetoKg != null && pesoBrutoKg != null;
    }
}

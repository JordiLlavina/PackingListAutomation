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
    // Opcionales según cliente (null si el JSON no los trae): talla del
    // artículo (cinturones AMI, SIZE de APC), nombre comercial del modelo,
    // código de livraison de APC y canal de la línea (DESTINATION de APC).
    private String talla;
    private String modelo;
    private String livraisonCode;
    private String canal;

    // Constructor por defecto (para creación con setters)
    public CajaData() {
    }

    // Constructor para pruebas y creación simplificada
    public CajaData(String referencia, String codigoColor, String talla, int cantidad, Double pesoNetoKg, Double pesoBrutoKg) {
        this.referencia = referencia;
        this.codigoColor = codigoColor;
        this.talla = talla;
        this.cantidad = cantidad;
        this.pesoNetoKg = pesoNetoKg;
        this.pesoBrutoKg = pesoBrutoKg;
    }

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

    public String getTalla() { return talla; }
    public void setTalla(String talla) { this.talla = talla; }

    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }

    public String getLivraisonCode() { return livraisonCode; }
    public void setLivraisonCode(String livraisonCode) { this.livraisonCode = livraisonCode; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public boolean tienePesosCompletos() {
        return pesoNetoKg != null && pesoBrutoKg != null;
    }
}

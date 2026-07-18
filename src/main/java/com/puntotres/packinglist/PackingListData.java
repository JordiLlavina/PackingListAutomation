package com.puntotres.packinglist;

import java.util.List;

/**
 * Datos de entrada del packing list AMI, tal como llegan en el JSON.
 *
 * Los datos del proveedor (nombre, código, ciudad, país) NO están aquí:
 * son fijos y viven como constantes en {@link AmiExcelBuilder}.
 *
 * Las fechas se reciben como String en formato dd/MM/yyyy; es el builder
 * quien las convierte a fecha real de Excel.
 */
public class PackingListData {

    private String destino;
    private String temporada;
    private String numeroFactura;
    private String fechaFactura;
    private String fechaEnvio;
    private List<Caja> cajas;

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public String getTemporada() { return temporada; }
    public void setTemporada(String temporada) { this.temporada = temporada; }

    public String getNumeroFactura() { return numeroFactura; }
    public void setNumeroFactura(String numeroFactura) { this.numeroFactura = numeroFactura; }

    public String getFechaFactura() { return fechaFactura; }
    public void setFechaFactura(String fechaFactura) { this.fechaFactura = fechaFactura; }

    public String getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(String fechaEnvio) { this.fechaEnvio = fechaEnvio; }

    public List<Caja> getCajas() { return cajas; }
    public void setCajas(List<Caja> cajas) { this.cajas = cajas; }

    /**
     * Una línea del packing list: una caja física.
     * tamanoCaja llega como "LxWxH" en centímetros, p. ej. "60x40x30".
     * numeroPedido, referencia y codigoColor son por caja: un packing list
     * (destinación + modelo + color) puede mezclar pedidos.
     * Los pesos son Double: null = desconocido, la celda queda vacía.
     */
    public static class Caja {

        private int numeroCaja;
        private String numeroPedido;
        private String referencia;
        private String codigoColor;
        private int cantidad;
        private String tamanoCaja;
        private Double pesoNetoKg;
        private Double pesoBrutoKg;

        public int getNumeroCaja() { return numeroCaja; }
        public void setNumeroCaja(int numeroCaja) { this.numeroCaja = numeroCaja; }

        public String getNumeroPedido() { return numeroPedido; }
        public void setNumeroPedido(String numeroPedido) { this.numeroPedido = numeroPedido; }

        public String getReferencia() { return referencia; }
        public void setReferencia(String referencia) { this.referencia = referencia; }

        public String getCodigoColor() { return codigoColor; }
        public void setCodigoColor(String codigoColor) { this.codigoColor = codigoColor; }

        public int getCantidad() { return cantidad; }
        public void setCantidad(int cantidad) { this.cantidad = cantidad; }

        public String getTamanoCaja() { return tamanoCaja; }
        public void setTamanoCaja(String tamanoCaja) { this.tamanoCaja = tamanoCaja; }

        public Double getPesoNetoKg() { return pesoNetoKg; }
        public void setPesoNetoKg(Double pesoNetoKg) { this.pesoNetoKg = pesoNetoKg; }

        public Double getPesoBrutoKg() { return pesoBrutoKg; }
        public void setPesoBrutoKg(Double pesoBrutoKg) { this.pesoBrutoKg = pesoBrutoKg; }
    }
}

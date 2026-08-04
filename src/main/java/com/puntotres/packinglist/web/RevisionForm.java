package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo editado a mano en la pantalla de revisión. Cada entrada apunta a las cajas
 * de UNA fila de la tabla por (indiceDestino, indicesCaja) — la POSICIÓN de cada
 * caja en la lista de su destinación, no su número: el número de caja puede
 * repetirse (caja mixta con dos colores).
 *
 * indicesCaja es una LISTA porque una fila puede representar un tramo de cajas
 * compactado ("4-8"): lo tecleado ahí vale para las cinco.
 *
 * Un campo que llega vacío significa "no tocar lo que haya". Por eso los
 * numéricos son Integer/Double y no primitivos, aunque en {@code CajaData}
 * algunos sean primitivos: es la única forma de distinguir "vacío" de "cero".
 */
public class RevisionForm {

    private List<CajaEditada> cajas = new ArrayList<>();

    /**
     * Lo editado en la CABECERA de cada destinación (hoy solo el Livraison
     * code de APC). Va aparte de las cajas porque es un dato de la destinación
     * entera: como columna se repetiría en cada fila y ensuciaría el criterio
     * de compactación de {@link AgrupadorFilasRevision}.
     */
    private List<DestinoEditado> destinos = new ArrayList<>();

    public List<CajaEditada> getCajas() { return cajas; }
    public void setCajas(List<CajaEditada> cajas) { this.cajas = cajas; }

    public List<DestinoEditado> getDestinos() { return destinos; }
    public void setDestinos(List<DestinoEditado> destinos) { this.destinos = destinos; }

    public static class DestinoEditado {

        private int indiceDestino;
        private String livraisonCode;

        public int getIndiceDestino() { return indiceDestino; }
        public void setIndiceDestino(int indiceDestino) { this.indiceDestino = indiceDestino; }

        public String getLivraisonCode() { return livraisonCode; }
        public void setLivraisonCode(String livraisonCode) { this.livraisonCode = livraisonCode; }
    }

    public static class CajaEditada {

        private int indiceDestino;
        private List<Integer> indicesCaja = new ArrayList<>();
        private Integer numeroCaja;
        private String referencia;
        private String codigoColor;
        private String numeroPedido;
        private String talla;
        private String tamanoCaja;
        private Integer cantidad;
        private Integer numeroPalet;
        private Double pesoNetoKg;
        private Double pesoBrutoKg;

        public int getIndiceDestino() { return indiceDestino; }
        public void setIndiceDestino(int indiceDestino) { this.indiceDestino = indiceDestino; }

        public List<Integer> getIndicesCaja() { return indicesCaja; }
        public void setIndicesCaja(List<Integer> indicesCaja) { this.indicesCaja = indicesCaja; }

        public Integer getNumeroCaja() { return numeroCaja; }
        public void setNumeroCaja(Integer numeroCaja) { this.numeroCaja = numeroCaja; }

        public String getReferencia() { return referencia; }
        public void setReferencia(String referencia) { this.referencia = referencia; }

        public String getCodigoColor() { return codigoColor; }
        public void setCodigoColor(String codigoColor) { this.codigoColor = codigoColor; }

        public String getNumeroPedido() { return numeroPedido; }
        public void setNumeroPedido(String numeroPedido) { this.numeroPedido = numeroPedido; }

        public String getTalla() { return talla; }
        public void setTalla(String talla) { this.talla = talla; }

        public String getTamanoCaja() { return tamanoCaja; }
        public void setTamanoCaja(String tamanoCaja) { this.tamanoCaja = tamanoCaja; }

        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

        public Integer getNumeroPalet() { return numeroPalet; }
        public void setNumeroPalet(Integer numeroPalet) { this.numeroPalet = numeroPalet; }

        public Double getPesoNetoKg() { return pesoNetoKg; }
        public void setPesoNetoKg(Double pesoNetoKg) { this.pesoNetoKg = pesoNetoKg; }

        public Double getPesoBrutoKg() { return pesoBrutoKg; }
        public void setPesoBrutoKg(Double pesoBrutoKg) { this.pesoBrutoKg = pesoBrutoKg; }
    }
}

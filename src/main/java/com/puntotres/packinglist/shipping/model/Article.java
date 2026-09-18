package com.puntotres.packinglist.shipping.model;

import java.math.BigDecimal;

import jakarta.persistence.*;

/** Artículo del catálogo aduanero: lo que hace falta para rellenar una línea de aduana sin teclearla. */
@Entity
@Table(name = "article", uniqueConstraints = @UniqueConstraint(name = "uk_article_referencia", columnNames = "referencia"))
public class Article {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referencia", length = 60, nullable = false)
    private String referencia;

    @Column(name = "descripcion_aduanera", length = 255, nullable = false)
    private String descripcionAduanera;

    @Column(name = "codigo_hs", length = 20, nullable = false)
    private String codigoHs;

    @Column(name = "pais_origen", length = 2, nullable = false)
    private String paisOrigen;

    @Column(name = "peso_neto_unitario")
    private Double pesoNetoUnitario;

    @Column(name = "peso_bruto_unitario")
    private Double pesoBrutoUnitario;

    @Column(name = "valor_unitario", precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "moneda", length = 3, nullable = false)
    private String moneda = "EUR";

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected Article() { }

    public Article(String referencia, String descripcionAduanera, String codigoHs, String paisOrigen) {
        this.referencia = referencia;
        this.descripcionAduanera = descripcionAduanera;
        this.codigoHs = codigoHs;
        this.paisOrigen = paisOrigen;
    }

    public Long getId() { return id; }
    public String getReferencia() { return referencia; }
    public String getDescripcionAduanera() { return descripcionAduanera; }
    public String getCodigoHs() { return codigoHs; }
    public String getPaisOrigen() { return paisOrigen; }
    public Double getPesoNetoUnitario() { return pesoNetoUnitario; }
    public Double getPesoBrutoUnitario() { return pesoBrutoUnitario; }
    public BigDecimal getValorUnitario() { return valorUnitario; }
    public String getMoneda() { return moneda; }
    public boolean isActivo() { return activo; }
    public void setReferencia(String v) { referencia = v; }
    public void setDescripcionAduanera(String v) { descripcionAduanera = v; }
    public void setCodigoHs(String v) { codigoHs = v; }
    public void setPaisOrigen(String v) { paisOrigen = v; }
    public void setPesoNetoUnitario(Double v) { pesoNetoUnitario = v; }
    public void setPesoBrutoUnitario(Double v) { pesoBrutoUnitario = v; }
    public void setValorUnitario(BigDecimal v) { valorUnitario = v; }
    public void setMoneda(String v) { moneda = v; }
    public void setActivo(boolean v) { activo = v; }
}

package com.puntotres.packinglist.shipping.model;

import java.math.BigDecimal;

import jakarta.persistence.*;

/** Una línea de la declaración de exportación (factura comercial). */
@Entity
@Table(name = "customs_line_item")
public class CustomsLineItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "descripcion", length = 255) private String descripcion;
    @Column(name = "codigo_hs", length = 20) private String codigoHs;
    @Column(name = "cantidad") private Integer cantidad;
    @Column(name = "unidad", length = 5, nullable = false) private String unidad = "PCS";
    @Column(name = "valor_unitario", precision = 12, scale = 2) private BigDecimal valorUnitario;
    @Column(name = "moneda", length = 3, nullable = false) private String moneda = "EUR";
    @Column(name = "peso_neto") private Double pesoNeto;
    @Column(name = "peso_bruto") private Double pesoBruto;
    @Column(name = "pais_origen", length = 2) private String paisOrigen;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "article_id")
    private Article articulo;

    protected CustomsLineItem() { }

    public CustomsLineItem(String descripcion, String codigoHs, Integer cantidad, BigDecimal valorUnitario,
                           Double pesoNeto, Double pesoBruto, String paisOrigen, Article articulo) {
        this.descripcion = descripcion; this.codigoHs = codigoHs; this.cantidad = cantidad;
        this.valorUnitario = valorUnitario; this.pesoNeto = pesoNeto; this.pesoBruto = pesoBruto;
        this.paisOrigen = paisOrigen == null ? null : paisOrigen.toUpperCase(); this.articulo = articulo;
    }

    /** cantidad × valor unitario, o cero si falta algo. */
    public BigDecimal valorTotal() {
        if (cantidad == null || valorUnitario == null) return BigDecimal.ZERO;
        return valorUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public boolean completa() {
        return descripcion != null && !descripcion.isBlank() && codigoHs != null && !codigoHs.isBlank()
                && cantidad != null && cantidad > 0 && valorUnitario != null && paisOrigen != null && !paisOrigen.isBlank();
    }

    public Long getId() { return id; }
    public String getDescripcion() { return descripcion; }
    public String getCodigoHs() { return codigoHs; }
    public Integer getCantidad() { return cantidad; }
    public String getUnidad() { return unidad; }
    public BigDecimal getValorUnitario() { return valorUnitario; }
    public String getMoneda() { return moneda; }
    public Double getPesoNeto() { return pesoNeto; }
    public Double getPesoBruto() { return pesoBruto; }
    public String getPaisOrigen() { return paisOrigen; }
    public Article getArticulo() { return articulo; }
    public Long getArticuloId() { return articulo == null ? null : articulo.getId(); }
}

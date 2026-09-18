package com.puntotres.packinglist.shipping.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

/** Un producto de DHL con su precio, tal cual lo devolvió la tarifa. Se borra al volver a DRAFT. */
@Entity
@Table(name = "rate_offer")
public class RateOffer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "producto_codigo", length = 10, nullable = false) private String productoCodigo;
    @Column(name = "producto_nombre", length = 80, nullable = false) private String productoNombre;
    @Column(name = "precio", precision = 12, scale = 2) private BigDecimal precio;
    @Column(name = "moneda", length = 3) private String moneda;
    @Column(name = "entrega_estimada") private LocalDateTime entregaEstimada;

    protected RateOffer() { }

    public RateOffer(String productoCodigo, String productoNombre, BigDecimal precio, String moneda, LocalDateTime entregaEstimada) {
        this.productoCodigo = productoCodigo; this.productoNombre = productoNombre;
        this.precio = precio; this.moneda = moneda; this.entregaEstimada = entregaEstimada;
    }

    public Long getId() { return id; }
    public String getProductoCodigo() { return productoCodigo; }
    public String getProductoNombre() { return productoNombre; }
    public BigDecimal getPrecio() { return precio; }
    public String getMoneda() { return moneda; }
    public LocalDateTime getEntregaEstimada() { return entregaEstimada; }
}

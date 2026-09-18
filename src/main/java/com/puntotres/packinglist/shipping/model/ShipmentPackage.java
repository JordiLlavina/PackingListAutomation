package com.puntotres.packinglist.shipping.model;

import jakarta.persistence.*;

/** Un bulto físico: peso en kg y medidas en cm. */
@Entity
@Table(name = "shipment_package")
public class ShipmentPackage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "peso") private Double peso;
    @Column(name = "largo") private Double largo;
    @Column(name = "ancho") private Double ancho;
    @Column(name = "alto") private Double alto;
    @Column(name = "descripcion", length = 70) private String descripcion;

    protected ShipmentPackage() { }

    public ShipmentPackage(Double peso, Double largo, Double ancho, Double alto, String descripcion) {
        this.peso = peso; this.largo = largo; this.ancho = ancho; this.alto = alto; this.descripcion = descripcion;
    }

    public boolean completo() { return peso != null && largo != null && ancho != null && alto != null; }

    public Long getId() { return id; }
    public Double getPeso() { return peso; }
    public Double getLargo() { return largo; }
    public Double getAncho() { return ancho; }
    public Double getAlto() { return alto; }
    public String getDescripcion() { return descripcion; }
}

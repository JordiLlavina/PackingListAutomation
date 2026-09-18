package com.puntotres.packinglist.shipping.model;

import jakarta.persistence.*;

/**
 * Un PDF devuelto por el transportista (label, invoice, waybillDoc...). Se
 * guarda entero en la base y no como ruta: la base viaja de un ordenador a
 * otro y una ruta se rompe al mover la carpeta.
 */
@Entity
@Table(name = "shipment_document")
public class ShipmentDocument {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "shipment_id")
    private Shipment envio;

    @Column(name = "tipo", length = 20, nullable = false) private String tipo;
    @Column(name = "formato", length = 10, nullable = false) private String formato;
    @Lob @Column(name = "contenido", nullable = false) private byte[] contenido;

    protected ShipmentDocument() { }

    public ShipmentDocument(Shipment envio, String tipo, String formato, byte[] contenido) {
        this.envio = envio; this.tipo = tipo; this.formato = formato; this.contenido = contenido;
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public String getFormato() { return formato; }
    public byte[] getContenido() { return contenido; }
}

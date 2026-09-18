package com.puntotres.packinglist.shipping.model;

import jakarta.persistence.*;

/** Motivo del envío (muestras, producción...). motivoDhl es lo que va en la declaración de exportación. */
@Entity
@Table(name = "shipment_reason", uniqueConstraints = @UniqueConstraint(name = "uk_shipment_reason_nombre", columnNames = "nombre"))
public class ShipmentReason {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", length = 60, nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_dhl", length = 40, nullable = false)
    private ExportReasonType motivoDhl;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected ShipmentReason() { }

    public ShipmentReason(String nombre, ExportReasonType motivoDhl) {
        this.nombre = nombre;
        this.motivoDhl = motivoDhl;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public ExportReasonType getMotivoDhl() { return motivoDhl; }
    public boolean isActivo() { return activo; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setMotivoDhl(ExportReasonType motivoDhl) { this.motivoDhl = motivoDhl; }
    public void setActivo(boolean activo) { this.activo = activo; }
}

package com.puntotres.packinglist.shipping.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/** Una transición de estado, con quién y por qué. */
@Entity
@Table(name = "shipment_status_history")
public class ShipmentStatusHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(name = "estado_anterior", length = 12) private ShipmentStatus estadoAnterior;
    @Enumerated(EnumType.STRING) @Column(name = "estado_nuevo", length = 12, nullable = false) private ShipmentStatus estadoNuevo;
    @Column(name = "fecha", nullable = false) private LocalDateTime fecha = LocalDateTime.now();
    @Column(name = "responsable", length = 120) private String responsable;
    @Column(name = "detalle", length = 4000) private String detalle;

    protected ShipmentStatusHistory() { }

    public ShipmentStatusHistory(ShipmentStatus estadoAnterior, ShipmentStatus estadoNuevo, String responsable, String detalle) {
        this.estadoAnterior = estadoAnterior; this.estadoNuevo = estadoNuevo;
        this.responsable = responsable; this.detalle = detalle == null ? null : detalle.substring(0, Math.min(4000, detalle.length()));
    }

    public Long getId() { return id; }
    public ShipmentStatus getEstadoAnterior() { return estadoAnterior; }
    public ShipmentStatus getEstadoNuevo() { return estadoNuevo; }
    public LocalDateTime getFecha() { return fecha; }
    public String getResponsable() { return responsable; }
    public String getDetalle() { return detalle; }
}

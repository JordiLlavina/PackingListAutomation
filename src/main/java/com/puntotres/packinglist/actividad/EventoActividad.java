package com.puntotres.packinglist.actividad;

import java.time.OffsetDateTime;

import com.puntotres.packinglist.seguridad.Seccion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una acción con significado que alguien ha hecho con la aplicación. */
@Entity
@Table(name = "evento_actividad")
public class EventoActividad {

    /** Lo más largo que cabe en la columna detalle. */
    public static final int LARGO_DETALLE = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false)
    private String correo;

    @Column(nullable = false)
    private OffsetDateTime momento;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Seccion seccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private TipoEvento tipo;

    @Column(length = 64)
    private String cliente;

    private Integer cantidad;

    @Column(length = LARGO_DETALLE)
    private String detalle;

    protected EventoActividad() {
        // JPA
    }

    public EventoActividad(Long usuarioId, String correo, TipoEvento tipo, Seccion seccion,
                           String cliente, Integer cantidad, String detalle) {
        this.usuarioId = usuarioId;
        this.correo = correo;
        this.tipo = tipo;
        this.seccion = seccion;
        this.cliente = cliente;
        this.cantidad = cantidad;
        this.detalle = detalle;
        this.momento = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getCorreo() {
        return correo;
    }

    public OffsetDateTime getMomento() {
        return momento;
    }

    public Seccion getSeccion() {
        return seccion;
    }

    public TipoEvento getTipo() {
        return tipo;
    }

    public String getCliente() {
        return cliente;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public String getDetalle() {
        return detalle;
    }
}

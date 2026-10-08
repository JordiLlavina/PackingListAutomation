package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Un color de un cliente: su código, que es lo que trae el pedido ("LZZ"), y
 * su descripción, que el pedido no trae ("NOIR"). La pareja no cambia nunca,
 * así que se guarda una vez y vale para todas las temporadas. La descripción
 * puede estar vacía: es un color que ha llegado en un pedido y nadie ha
 * rellenado todavía.
 */
@Entity
@Table(name = "color_cliente")
@IdClass(ColorClienteId.class)
public class ColorCliente {

    @Id
    @Column(name = "cliente", length = 60)
    private String cliente;

    @Id
    @Column(name = "codigo", length = 40)
    private String codigo;

    @Column(name = "descripcion", length = 200, nullable = false)
    private String descripcion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected ColorCliente() {
        // Constructor para JPA.
    }

    public ColorCliente(String cliente, String codigo, String descripcion) {
        this.cliente = cliente;
        this.codigo = codigo;
        actualizar(descripcion);
    }

    public String getCliente() {
        return cliente;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void actualizar(String descripcion) {
        this.descripcion = descripcion == null ? "" : descripcion.trim();
        this.fechaActualizacion = LocalDateTime.now();
    }
}

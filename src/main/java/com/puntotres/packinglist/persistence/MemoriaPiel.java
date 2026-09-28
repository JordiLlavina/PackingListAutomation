package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Cómo se llama una piel del cliente. El pedido solo trae su referencia
 * ("AL0103"); el nombre lo teclea quien prepara el corte, y es del MATERIAL:
 * la misma piel se llama igual en todos los bolsos que la usan.
 */
@Entity
@Table(name = "memoria_piel")
@IdClass(MemoriaPielId.class)
public class MemoriaPiel {

    @Id
    @Column(name = "cliente", length = 60)
    private String cliente;

    @Id
    @Column(name = "ref_piel", length = 80)
    private String refPiel;

    @Column(name = "nombre", length = 200, nullable = false)
    private String nombre;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected MemoriaPiel() {
        // Constructor para JPA.
    }

    public MemoriaPiel(String cliente, String refPiel, String nombre) {
        this.cliente = cliente;
        this.refPiel = refPiel;
        actualizar(nombre);
    }

    public String getNombre() {
        return nombre;
    }

    public void actualizar(String nombre) {
        this.nombre = nombre;
        this.fechaActualizacion = LocalDateTime.now();
    }
}

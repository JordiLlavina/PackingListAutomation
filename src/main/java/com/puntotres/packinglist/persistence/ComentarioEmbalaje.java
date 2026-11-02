package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Un comentario tipo de la hoja de «Info de embalaje» («Cajas pesadas, no
 * apilar»): el usuario lo elige de un desplegable y su texto se añade al
 * apartado de comentarios de la hoja. El nombre es único.
 */
@Entity
@Table(name = "comentario_embalaje",
        uniqueConstraints = @UniqueConstraint(name = "uk_comentario_embalaje_nombre", columnNames = "nombre"))
public class ComentarioEmbalaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "texto", length = 4000, nullable = false)
    private String texto;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected ComentarioEmbalaje() {
        // Constructor para JPA.
    }

    public ComentarioEmbalaje(String nombre, String texto) {
        this.nombre = nombre;
        this.texto = texto;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTexto() {
        return texto;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void actualizar(String nombre, String texto) {
        this.nombre = nombre;
        this.texto = texto;
        this.fechaActualizacion = LocalDateTime.now();
    }
}

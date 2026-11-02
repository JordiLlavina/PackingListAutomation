package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Un modelo de un cliente: la forma del bolso ("ULL027", "F67008"), sin la
 * piel. Es el nivel al que pertenecen las fotos y el nombre del bolso; lo que
 * depende de la piel (cartón, unidades, forro, peso) va en {@link Articulo}.
 * Un modelo va en varias pieles —ULL754 en cuatro— y guardar aquí lo del
 * artículo mezclaría las de una con las de otra.
 */
@Entity
@Table(name = "modelo")
@IdClass(ModeloId.class)
public class Modelo {

    @Id
    @Column(name = "cliente", length = 60)
    private String cliente;

    @Id
    @Column(name = "codigo", length = 80)
    private String codigo;

    /** El nombre del bolso ("LE NEIGE", "AMI CANDY POUCH BAG"), si alguien lo ha dicho. */
    @Column(name = "nombre", length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoArticulo tipo;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected Modelo() {
        // Constructor para JPA.
    }

    public Modelo(String cliente, String codigo, String nombre, TipoArticulo tipo) {
        this.cliente = cliente;
        this.codigo = codigo;
        this.nombre = nombre;
        this.tipo = tipo;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public String getCliente() {
        return cliente;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre == null ? "" : nombre;
    }

    public TipoArticulo getTipo() {
        return tipo;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void setTipo(TipoArticulo tipo) {
        this.tipo = tipo;
        this.fechaActualizacion = LocalDateTime.now();
    }
}

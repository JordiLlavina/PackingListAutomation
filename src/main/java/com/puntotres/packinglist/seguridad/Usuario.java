package com.puntotres.packinglist.seguridad;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una persona del directorio de Puntotres que ha entrado alguna vez.
 *
 * La identidad es el oid de Entra, no el correo: el correo es solo un dato
 * que se refresca en cada entrada.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oid_entra", nullable = false, unique = true, length = 64)
    private String oidEntra;

    @Column(nullable = false)
    private String correo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private boolean administrador;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_alta", nullable = false)
    private OffsetDateTime fechaAlta;

    @Column(name = "ultimo_acceso")
    private OffsetDateTime ultimoAcceso;

    protected Usuario() {
        // JPA
    }

    public Usuario(String oidEntra, String correo, String nombre) {
        this.oidEntra = oidEntra;
        this.correo = correo;
        this.nombre = nombre;
        this.fechaAlta = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getOidEntra() {
        return oidEntra;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isAdministrador() {
        return administrador;
    }

    public void setAdministrador(boolean administrador) {
        this.administrador = administrador;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public OffsetDateTime getFechaAlta() {
        return fechaAlta;
    }

    public OffsetDateTime getUltimoAcceso() {
        return ultimoAcceso;
    }

    public void setUltimoAcceso(OffsetDateTime ultimoAcceso) {
        this.ultimoAcceso = ultimoAcceso;
    }
}

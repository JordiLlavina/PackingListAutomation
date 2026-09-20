package com.puntotres.packinglist.seguridad;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

    /**
     * Las secciones concedidas. Se cargan en la misma consulta que el usuario
     * porque el interceptor las necesita en cada petición y no tiene sentido
     * pagar una segunda ida a la base de datos por algo que son media docena
     * de cadenas.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_permiso", joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "seccion", length = 32, nullable = false)
    private Set<Seccion> permisos = EnumSet.noneOf(Seccion.class);

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

    public Set<Seccion> getPermisos() {
        return permisos;
    }

    public void conceder(Seccion seccion) {
        permisos.add(seccion);
    }

    public void retirar(Seccion seccion) {
        permisos.remove(seccion);
    }

    /**
     * ADMINISTRACION no se concede: la abre quien es administrador. Y ser
     * administrador no abre el resto de secciones — es poder repartir
     * permisos, no tenerlos todos.
     */
    public boolean puedeAbrir(Seccion seccion) {
        if (!activo) {
            return false;
        }
        if (seccion == Seccion.ADMINISTRACION) {
            return administrador;
        }
        return permisos.contains(seccion);
    }
}

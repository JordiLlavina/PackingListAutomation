package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un usuario de la app de modelos. Todos entran con login y contraseña; el
 * del taller, además, deja dado de alta el aparato desde el que entra (ver
 * {@link Dispositivo}) y no vuelve a teclearla en él. Más adelante podrá
 * entrar con su cuenta de Microsoft Entra ({@link #getOidEntra()}), y por eso
 * login y contraseña pueden faltar en la base (también en los usuarios del
 * taller dados de alta antes de noviembre de 2026, que entraban con un código
 * y no tenían contraseña: no entran hasta que el admin les pone una).
 *
 * La contraseña nunca se guarda: solo su hash BCrypt. Un usuario no se borra,
 * se desactiva ({@link #desactivar()}): sus mensajes y modificaciones lo
 * siguen nombrando.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    @Column(name = "login", length = 80)
    private String login;

    /** Hash BCrypt de la contraseña; null si el usuario no entra con contraseña. */
    @Column(name = "password_hash", length = 200)
    private String passwordHash;

    @Column(name = "correo", length = 200)
    private String correo;

    /** Identificador del usuario en Microsoft Entra. */
    @Column(name = "oid_entra", length = 80)
    private String oidEntra;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", length = 20, nullable = false)
    private Rol rol;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDateTime fechaAlta;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    protected Usuario() {
        // Constructor para JPA.
    }

    public Usuario(String nombre, String login, String passwordHash, Rol rol) {
        this.nombre = nombre;
        this.login = login;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.activo = true;
        this.fechaAlta = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getLogin() {
        return login;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getCorreo() {
        return correo;
    }

    public String getOidEntra() {
        return oidEntra;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }

    public LocalDateTime getUltimoAcceso() {
        return ultimoAcceso;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /** Deja de poder entrar; sus mensajes y modificaciones lo siguen nombrando. */
    public void desactivar() {
        this.activo = false;
    }

    /** Vuelve a poder entrar. */
    public void activar() {
        this.activo = true;
    }

    public void cambiarRol(Rol rol) {
        this.rol = rol;
    }

    /** Recibe el hash, nunca la contraseña: quien hashea es el servicio. */
    public void cambiarPassword(String hash) {
        this.passwordHash = hash;
    }

    /** Apunta que acaba de entrar. */
    public void tocarAcceso() {
        this.ultimoAcceso = LocalDateTime.now();
    }
}

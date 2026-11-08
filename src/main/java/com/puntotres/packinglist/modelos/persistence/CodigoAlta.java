package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un código de seis cifras para dar de alta un {@link Dispositivo}: el
 * despacho lo genera para un usuario, se teclea en el aparato y vale una sola
 * vez, durante poco tiempo. No es un secreto que dure, por eso se guarda en
 * claro; lo que dura es el token del dispositivo, y ese solo va como hash.
 */
@Entity
@Table(name = "codigo_alta")
public class CodigoAlta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "codigo", length = 6, nullable = false)
    private String codigo;

    @Column(name = "caduca", nullable = false)
    private LocalDateTime caduca;

    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

    protected CodigoAlta() {
        // Constructor para JPA.
    }

    public CodigoAlta(Long usuarioId, String codigo, LocalDateTime caduca) {
        this.usuarioId = usuarioId;
        this.codigo = codigo;
        this.caduca = caduca;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getCodigo() {
        return codigo;
    }

    public LocalDateTime getCaduca() {
        return caduca;
    }

    public LocalDateTime getUsadoEn() {
        return usadoEn;
    }

    /** Sin usar y sin caducar en ese momento. */
    public boolean vigente(LocalDateTime ahora) {
        return usadoEn == null && caduca.isAfter(ahora);
    }

    public void usar(LocalDateTime cuando) {
        this.usadoEn = cuando;
    }
}

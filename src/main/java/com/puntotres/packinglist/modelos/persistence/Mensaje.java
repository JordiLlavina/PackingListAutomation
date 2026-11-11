package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Lo que se dice en una modificación: el primero es el cuerpo con el que se
 * creó y los siguientes, las respuestas. Sus fotos, notas de voz y
 * documentos son {@link Adjunto}s.
 */
@Entity
@Table(name = "mensaje")
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "modificacion_id", nullable = false)
    private Long modificacionId;

    /** Quién lo escribió; null si no lo escribió una persona. */
    @Column(name = "autor_id")
    private Long autorId;

    /** Vacío, nunca null, cuando todo lo que dice va en los adjuntos. */
    @Column(name = "texto")
    private String texto;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    protected Mensaje() {
        // Constructor para JPA.
    }

    public Mensaje(Long modificacionId, Long autorId, String texto, LocalDateTime creadoEn) {
        this.modificacionId = modificacionId;
        this.autorId = autorId;
        this.texto = texto;
        this.creadoEn = creadoEn;
    }

    public Long getId() {
        return id;
    }

    public Long getModificacionId() {
        return modificacionId;
    }

    public Long getAutorId() {
        return autorId;
    }

    public String getTexto() {
        return texto;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}

package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un documento de una {@link FichaPiel}: la ficha técnica o la oferta del
 * proveedor, tal como llegó. Se guarda aunque nadie lo lea todavía: es el
 * original del que salieron los datos, y si uno está mal se puede ir a él.
 */
@Entity
@Table(name = "documento_piel")
public class DocumentoPiel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ficha_piel_id", nullable = false)
    private Long fichaPielId;

    @Column(name = "nombre_fichero", length = 255, nullable = false)
    private String nombreFichero;

    @Column(name = "mime", length = 120)
    private String mime;

    @Column(name = "tamano", nullable = false)
    private int tamano;

    @Column(name = "bytes", nullable = false)
    private byte[] bytes;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    protected DocumentoPiel() {
        // Constructor para JPA.
    }

    public DocumentoPiel(Long fichaPielId, String nombreFichero, String mime, byte[] bytes) {
        this.fichaPielId = fichaPielId;
        this.nombreFichero = nombreFichero;
        this.mime = mime;
        this.bytes = bytes;
        this.tamano = bytes.length;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getFichaPielId() { return fichaPielId; }
    public String getNombreFichero() { return nombreFichero; }
    public String getMime() { return mime == null ? "" : mime; }
    public int getTamano() { return tamano; }
    public byte[] getBytes() { return bytes; }
    public LocalDateTime getFecha() { return fecha; }
}

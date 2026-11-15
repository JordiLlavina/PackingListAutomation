package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una foto de una {@link FichaPiel}, ya reducida (JPEG de 1.600 px de lado
 * como mucho) y dentro de la base de datos, como las del catálogo. Sin
 * {@code @Lob}: la columna es {@code bytea} y en PostgreSQL {@code @Lob}
 * sobre {@code byte[]} crearía un objeto grande aparte de la fila.
 */
@Entity
@Table(name = "foto_piel")
public class FotoPiel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ficha_piel_id", nullable = false)
    private Long fichaPielId;

    @Column(name = "nombre_fichero", length = 255, nullable = false)
    private String nombreFichero;

    @Column(name = "principal", nullable = false)
    private boolean principal;

    @Column(name = "orden", nullable = false)
    private int orden;

    @Column(name = "bytes", nullable = false)
    private byte[] jpeg;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected FotoPiel() {
        // Constructor para JPA.
    }

    public FotoPiel(Long fichaPielId, String nombreFichero, int orden, byte[] jpeg) {
        this.fichaPielId = fichaPielId;
        this.nombreFichero = nombreFichero;
        this.orden = orden;
        this.jpeg = jpeg;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getFichaPielId() { return fichaPielId; }
    public String getNombreFichero() { return nombreFichero; }
    public boolean isPrincipal() { return principal; }
    public int getOrden() { return orden; }
    public byte[] getJpeg() { return jpeg; }

    public void setPrincipal(boolean principal) {
        this.principal = principal;
        this.fechaActualizacion = LocalDateTime.now();
    }
}

package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Una foto de un modelo, ya reducida (JPEG de 1.600 px de lado como mucho,
 * como las del corte), guardada dentro de la base de datos y no como ruta al
 * disco: así ./datos viaja entero de un ordenador a otro, igual que el excel
 * de las temporadas. Tabla aparte de {@link Modelo} para que listar modelos
 * no cargue los bytes; para listar está {@link ResumenFoto}.
 *
 * La foto es del MODELO, no de la piel: la carpeta "ULL027" vale para
 * ULL027.AL0103 y ULL027.AL0216. Como mucho una por modelo es la principal;
 * lo garantiza el servicio y no la base de datos.
 */
@Entity
@Table(name = "foto_modelo")
public class FotoCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "modelo_id", nullable = false)
    private Long modeloId;

    @Column(name = "nombre_fichero", length = 255, nullable = false)
    private String nombreFichero;

    @Column(name = "principal", nullable = false)
    private boolean principal;

    @Column(name = "orden", nullable = false)
    private int orden;

    @Lob
    @Column(name = "bytes", nullable = false)
    private byte[] jpeg;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected FotoCatalogo() {
        // Constructor para JPA.
    }

    public FotoCatalogo(Long modeloId, String nombreFichero, int orden, byte[] jpeg) {
        this.modeloId = modeloId;
        this.nombreFichero = nombreFichero;
        this.orden = orden;
        this.jpeg = jpeg;
        this.principal = false;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getModeloId() {
        return modeloId;
    }

    public String getNombreFichero() {
        return nombreFichero;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public int getOrden() {
        return orden;
    }

    public byte[] getJpeg() {
        return jpeg;
    }

    public void setPrincipal(boolean principal) {
        this.principal = principal;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public ResumenFoto resumen() {
        return new ResumenFoto(id, modeloId, nombreFichero, principal, orden);
    }
}

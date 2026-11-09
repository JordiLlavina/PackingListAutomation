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
 * Una foto, una nota de voz o un documento de un {@link Mensaje}, con sus
 * bytes dentro de la base de datos (como las fotos del catálogo). Para listar
 * está {@link ResumenAdjunto}, que no los carga.
 *
 * Una nota de voz lleva además su transcripción y el resumen de la IA, que
 * llegan después: {@link #getEstadoTranscripcion()} dice por dónde va.
 */
@Entity
@Table(name = "adjunto")
public class Adjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "mensaje_id", nullable = false)
    private Long mensajeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoAdjunto tipo;

    @Column(name = "nombre", length = 255, nullable = false)
    private String nombre;

    @Column(name = "mime", length = 100, nullable = false)
    private String mime;

    @Column(name = "tamano", nullable = false)
    private long tamano;

    /** Solo en las notas de voz. */
    @Column(name = "duracion_seg")
    private Integer duracionSeg;

    @Column(name = "bytes", nullable = false)
    private byte[] bytes;

    @Column(name = "transcripcion")
    private String transcripcion;

    @Column(name = "resumen_ia")
    private String resumenIa;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_transcripcion", length = 20)
    private EstadoTranscripcion estadoTranscripcion;

    /** Por qué falló la transcripción; null si no falló. */
    @Column(name = "error")
    private String error;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    protected Adjunto() {
        // Constructor para JPA.
    }

    public Adjunto(Long mensajeId, TipoAdjunto tipo, String nombre, String mime, byte[] bytes, Integer duracionSeg,
                   EstadoTranscripcion estadoTranscripcion, LocalDateTime fecha) {
        this.mensajeId = mensajeId;
        this.tipo = tipo;
        this.nombre = nombre;
        this.mime = mime;
        this.bytes = bytes;
        this.tamano = bytes.length;
        this.duracionSeg = duracionSeg;
        this.estadoTranscripcion = estadoTranscripcion;
        this.fecha = fecha;
    }

    public Long getId() {
        return id;
    }

    public Long getMensajeId() {
        return mensajeId;
    }

    public TipoAdjunto getTipo() {
        return tipo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getMime() {
        return mime;
    }

    public long getTamano() {
        return tamano;
    }

    public Integer getDuracionSeg() {
        return duracionSeg;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getTranscripcion() {
        return transcripcion;
    }

    public String getResumenIa() {
        return resumenIa;
    }

    public EstadoTranscripcion getEstadoTranscripcion() {
        return estadoTranscripcion;
    }

    public String getError() {
        return error;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    /** Cómo ha ido la transcripción: su estado, el texto si salió y el error si no. */
    public void marcarTranscripcion(EstadoTranscripcion estado, String transcripcion, String error) {
        this.estadoTranscripcion = estado;
        this.transcripcion = transcripcion;
        this.error = error;
    }

    public void marcarResumen(String resumenIa) {
        this.resumenIa = resumenIa;
    }
}

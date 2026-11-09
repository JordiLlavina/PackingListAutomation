package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.puntotres.packinglist.modelos.api.dto.DocumentoResumen;

/**
 * Un documento de un modelo (la ficha técnica del cliente, un PDF de medidas,
 * una hoja de cálculo), con sus bytes dentro de la base de datos como las
 * fotos y los adjuntos. Para listar está {@link DocumentoResumen}, que no
 * los carga.
 *
 * Se guarda tal cual llega, sea del tipo que sea: quien lo sirve decide si se
 * abre en el navegador o se descarga.
 */
@Entity
@Table(name = "documento_modelo")
public class DocumentoModelo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "modelo_id", nullable = false)
    private Long modeloId;

    @Column(name = "nombre", length = 255, nullable = false)
    private String nombre;

    @Column(name = "mime", length = 100, nullable = false)
    private String mime;

    @Column(name = "tamano", nullable = false)
    private long tamano;

    @Column(name = "bytes", nullable = false)
    private byte[] bytes;

    /** Quién lo subió; null si no se sabe. */
    @Column(name = "subido_por")
    private Long subidoPor;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    protected DocumentoModelo() {
        // Constructor para JPA.
    }

    public DocumentoModelo(Long modeloId, String nombre, String mime, byte[] bytes, Long subidoPor,
                           LocalDateTime fecha) {
        this.modeloId = modeloId;
        this.nombre = nombre;
        this.mime = mime;
        this.bytes = bytes;
        this.tamano = bytes.length;
        this.subidoPor = subidoPor;
        this.fecha = fecha;
    }

    public Long getId() {
        return id;
    }

    public Long getModeloId() {
        return modeloId;
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

    public byte[] getBytes() {
        return bytes;
    }

    public Long getSubidoPor() {
        return subidoPor;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    /** Pasa a otro modelo: al juntar dos, los documentos del que desaparece se van al que se queda. */
    public void moverA(Long modeloId) {
        this.modeloId = modeloId;
    }

    public DocumentoResumen resumen() {
        return new DocumentoResumen(id, nombre, mime, tamano, fecha);
    }
}

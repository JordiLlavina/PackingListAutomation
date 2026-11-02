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
 * Una hoja de «Info de embalaje» ya generada.
 *
 * Las columnas sueltas son las que se listan y filtran; {@code datos} es el
 * snapshot entero en JSON (ver {@code DatosHojaEmbalaje}), para que una hoja
 * antigua enseñe lo que de verdad se mandó aunque el pedido o la memoria del
 * artículo hayan cambiado. La foto no viaja en él: se busca en el catálogo por
 * {@code codigoModelo}. {@code temporadaId} no es una FK a propósito: si la
 * temporada se borra, la hoja se queda.
 */
@Entity
@Table(name = "hoja_embalaje")
public class HojaEmbalaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cliente", length = 60, nullable = false)
    private String cliente;

    @Column(name = "temporada", length = 60, nullable = false)
    private String temporada;

    /** La temporada guardada de la que salió el pedido, si había; sin clave ajena. */
    @Column(name = "temporada_id")
    private Long temporadaId;

    @Column(name = "numero_envio", nullable = false)
    private int numeroEnvio;

    @Column(name = "nota", length = 60)
    private String nota;

    @Column(name = "taller", length = 60)
    private String taller;

    @Column(name = "referencia", length = 80, nullable = false)
    private String referencia;

    @Column(name = "codigo_color", length = 200, nullable = false)
    private String codigoColor;

    @Column(name = "codigo_modelo", length = 80)
    private String codigoModelo;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** JSON de {@code DatosHojaEmbalaje}: lo que se imprimió. */
    @Lob
    @Column(name = "datos", nullable = false)
    private String datos;

    protected HojaEmbalaje() {
        // Constructor para JPA.
    }

    public HojaEmbalaje(String cliente, String temporada, Long temporadaId, int numeroEnvio, String nota,
                        String taller, String referencia, String codigoColor, String codigoModelo, String datos) {
        this.cliente = cliente;
        this.temporada = temporada;
        this.temporadaId = temporadaId;
        this.numeroEnvio = numeroEnvio;
        this.nota = nota;
        this.taller = taller;
        this.referencia = referencia;
        this.codigoColor = codigoColor;
        this.codigoModelo = codigoModelo;
        this.datos = datos;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCliente() {
        return cliente;
    }

    public String getTemporada() {
        return temporada;
    }

    public Long getTemporadaId() {
        return temporadaId;
    }

    public int getNumeroEnvio() {
        return numeroEnvio;
    }

    public String getNota() {
        return nota;
    }

    public String getTaller() {
        return taller;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getCodigoColor() {
        return codigoColor;
    }

    public String getCodigoModelo() {
        return codigoModelo;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public String getDatos() {
        return datos;
    }
}

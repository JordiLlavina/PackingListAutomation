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
 * Una Hoja de corte de un modelo ya generada. Las columnas sueltas son las
 * del listado de antiguas; {@code datos} es el snapshot entero en JSON (ver
 * {@code DatosHojaCorte}), como en {@link HojaProduccion}.
 */
@Entity
@Table(name = "hoja_corte")
public class HojaCorte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cliente", length = 60, nullable = false)
    private String cliente;

    @Column(name = "temporada", length = 60)
    private String temporada;

    @Column(name = "modelo", length = 80)
    private String modelo;

    @Column(name = "nombre_bolso", length = 200)
    private String nombreBolso;

    /** Cuántas líneas de color con cantidad lleva: el listado lo dice. */
    @Column(name = "colores", nullable = false)
    private int colores;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Lob
    @Column(name = "datos", nullable = false)
    private String datos;

    /** La foto subida en el formulario, o null si usa la del catálogo. Sin {@code @Lob}: es {@code bytea}. */
    @Column(name = "foto")
    private byte[] foto;

    protected HojaCorte() {
        // Constructor para JPA.
    }

    public HojaCorte(String cliente, String temporada, String modelo, String nombreBolso, int colores, String datos,
                     byte[] foto) {
        this.cliente = cliente;
        this.temporada = temporada;
        this.modelo = modelo;
        this.nombreBolso = nombreBolso;
        this.colores = colores;
        this.datos = datos;
        this.foto = foto;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCliente() {
        return cliente;
    }

    public String getDatos() {
        return datos;
    }

    public byte[] getFoto() {
        return foto;
    }
}

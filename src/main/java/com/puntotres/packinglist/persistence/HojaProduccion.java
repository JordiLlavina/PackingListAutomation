package com.puntotres.packinglist.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Una Hoja de Producción ya generada para un taller externo.
 *
 * Las columnas sueltas son las que se listan y filtran; {@code datos} es el
 * snapshot entero en JSON (ver {@code DatosHojaProduccion}), para que una hoja
 * antigua enseñe y regenere lo que de verdad se mandó aunque el catálogo haya
 * cambiado.
 */
@Entity
@Table(name = "hoja_produccion")
public class HojaProduccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cliente", length = 60, nullable = false)
    private String cliente;

    @Column(name = "temporada", length = 60)
    private String temporada;

    /** El modelo de la hoja (una hoja = un modelo); null en las guardadas antes de la V14. */
    @Column(name = "modelo", length = 80)
    private String modelo;

    @Column(name = "nombre_bolso", length = 200)
    private String nombreBolso;

    @Column(name = "taller", length = 60, nullable = false)
    private String taller;

    @Column(name = "numero", nullable = false)
    private int numero;

    @Column(name = "fecha_salida")
    private LocalDate fechaSalida;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** JSON de {@code DatosHojaProduccion}: lo que se imprimió. */
    @Lob
    @Column(name = "datos", nullable = false)
    private String datos;

    /**
     * La foto subida en el formulario (JPEG ya reducido), o null si la hoja
     * usa la del catálogo. Sin {@code @Lob}: la columna es {@code bytea}. No
     * se lee al listar: el listado va por una proyección sin ella.
     */
    @Column(name = "foto")
    private byte[] foto;

    protected HojaProduccion() {
        // Constructor para JPA.
    }

    public HojaProduccion(String cliente, String temporada, String modelo, String nombreBolso, String taller, int numero,
                          LocalDate fechaSalida, String datos) {
        this.cliente = cliente;
        this.temporada = temporada;
        this.modelo = modelo;
        this.nombreBolso = nombreBolso;
        this.taller = taller;
        this.numero = numero;
        this.fechaSalida = fechaSalida;
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

    public String getModelo() {
        return modelo;
    }

    public String getNombreBolso() {
        return nombreBolso;
    }

    public String getTaller() {
        return taller;
    }

    public int getNumero() {
        return numero;
    }

    public LocalDate getFechaSalida() {
        return fechaSalida;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public String getDatos() {
        return datos;
    }

    public byte[] getFoto() {
        return foto;
    }

    public void setFoto(byte[] foto) {
        this.foto = foto;
    }
}

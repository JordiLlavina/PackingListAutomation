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
 * Un envío del asistente de packing list guardado tal como estaba en la
 * pantalla de revisión, para volver a él otro día.
 *
 * Mismo patrón que {@link HojaProduccion}: las columnas sueltas son las que se
 * listan y {@code datos} es el envío entero en JSON (ver
 * {@code SesionPackingDatos}).
 */
@Entity
@Table(name = "sesion_packing")
public class SesionPacking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cliente", length = 60, nullable = false)
    private String cliente;

    @Column(name = "temporada", length = 60)
    private String temporada;

    @Column(name = "facturas", length = 400)
    private String facturas;

    @Column(name = "destinos", length = 1000)
    private String destinos;

    @Column(name = "creada", nullable = false)
    private LocalDateTime creada;

    @Column(name = "modificada", nullable = false)
    private LocalDateTime modificada;

    /** JSON de {@code SesionPackingDatos}. */
    @Lob
    @Column(name = "datos", nullable = false)
    private String datos;

    /** El excel de pedido del envío, o null. Sin {@code @Lob}: la columna es {@code bytea}. */
    @Column(name = "excel_pedido")
    private byte[] excelPedido;

    @Column(name = "nombre_excel_pedido", length = 255)
    private String nombreExcelPedido;

    protected SesionPacking() {
        // Constructor para JPA.
    }

    public SesionPacking(String cliente) {
        this.cliente = cliente;
        this.creada = LocalDateTime.now();
        this.modificada = this.creada;
    }

    public Long getId() { return id; }
    public String getCliente() { return cliente; }
    public String getTemporada() { return temporada; }
    public String getFacturas() { return facturas; }
    public String getDestinos() { return destinos; }
    public LocalDateTime getCreada() { return creada; }
    public LocalDateTime getModificada() { return modificada; }
    public String getDatos() { return datos; }
    public byte[] getExcelPedido() { return excelPedido; }
    public String getNombreExcelPedido() { return nombreExcelPedido; }

    /** Lo que cambia con cada guardado: el resumen para la lista y el envío entero. */
    public void actualizar(String temporada, String facturas, String destinos, String datos) {
        this.temporada = temporada;
        this.facturas = facturas;
        this.destinos = destinos;
        this.datos = datos;
        this.modificada = LocalDateTime.now();
    }

    public void setExcelPedido(byte[] contenido, String nombre) {
        this.excelPedido = contenido;
        this.nombreExcelPedido = nombre;
    }
}

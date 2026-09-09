package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Una temporada de un cliente y el excel de pedido con el que se trabaja
 * durante toda ella.
 *
 * Existe para no volver a subir el mismo fichero en cada envío: el pedido de
 * una temporada es el mismo documento durante meses, y quien prepara un envío
 * lo tiene que ir a buscar al disco cada vez. Guardarlo aquí lo convierte en
 * elegir la temporada en un desplegable.
 *
 * El fichero se guarda entero en la base de datos y no como ruta a un fichero
 * del disco: una ruta se rompe en cuanto alguien mueve o renombra la carpeta,
 * y el fallo aparecería a mitad de un envío. Con la copia dentro, la base de
 * datos de {@code ./datos} se lleva de un ordenador a otro y las temporadas
 * viajan con ella.
 *
 * La clave es un id generado y no cliente+temporada: así se puede corregir el
 * nombre de una temporada mal tecleada sin borrarla y volver a subir el excel.
 * Que no haya dos temporadas iguales del mismo cliente lo garantizan la
 * restricción de aquí abajo y la comprobación —esta sí, sin distinguir
 * mayúsculas— de {@link ArchivoTemporadas}.
 */
@Entity
@Table(name = "temporada_guardada",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_temporada_por_cliente",
                columnNames = {"cliente", "temporada"}))
public class TemporadaGuardada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Clave del cliente en el catálogo de application.yml (AMI, APC...). */
    @Column(name = "cliente", length = 60, nullable = false)
    private String cliente;

    /** Como la llama el cliente: H26, FALL26, E25... Va tal cual a los excels. */
    @Column(name = "temporada", length = 60, nullable = false)
    private String temporada;

    @Column(name = "nombre_fichero", length = 255, nullable = false)
    private String nombreFichero;

    @Lob
    @Column(name = "excel", nullable = false)
    private byte[] excel;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected TemporadaGuardada() {
        // Constructor para JPA.
    }

    public TemporadaGuardada(String cliente, String temporada, String nombreFichero, byte[] excel) {
        this.cliente = cliente;
        this.temporada = temporada;
        this.nombreFichero = nombreFichero;
        this.excel = excel;
        this.fechaActualizacion = LocalDateTime.now();
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

    public String getNombreFichero() {
        return nombreFichero;
    }

    public byte[] getExcel() {
        return excel;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    /**
     * Corrige la temporada. Un excel nulo significa "deja el que ya tenía": en
     * la pantalla se edita el nombre sin volver a subir el fichero, y perderlo
     * por no haberlo readjuntado sería justo el trabajo que esto ahorra.
     */
    public void actualizar(String cliente, String temporada, String nombreFichero, byte[] excel) {
        this.cliente = cliente;
        this.temporada = temporada;
        if (excel != null) {
            this.nombreFichero = nombreFichero;
            this.excel = excel;
        }
        this.fechaActualizacion = LocalDateTime.now();
    }
}

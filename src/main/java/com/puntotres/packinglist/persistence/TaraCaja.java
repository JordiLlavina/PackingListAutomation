package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tara (peso del cartón vacío, en kg) de un tamaño de caja.
 *
 * La medida se guarda ya normalizada porque es la clave primaria: "60X40X40 "
 * de un excel y "60x40x40" del yml son el mismo cartón y no pueden acabar
 * como dos filas.
 *
 * La tara es un dato del almacén, no una constante del programa: entran
 * tamaños nuevos y los pesos se corrigen a medida que se pesa cada cartón.
 * Por eso vive en una tabla editable y no en el código.
 */
@Entity
@Table(name = "tara_caja")
public class TaraCaja {

    @Id
    @Column(name = "medida", length = 40)
    private String medida;

    @Column(name = "tara_kg", nullable = false)
    private double taraKg;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected TaraCaja() {
        // Constructor para JPA.
    }

    public TaraCaja(String medida, double taraKg) {
        this.medida = medida;
        this.taraKg = taraKg;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public String getMedida() {
        return medida;
    }

    public double getTaraKg() {
        return taraKg;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    /** Corrige el peso tras volver a pesar el cartón. */
    public void actualizar(double taraKg) {
        this.taraKg = taraKg;
        this.fechaActualizacion = LocalDateTime.now();
    }
}

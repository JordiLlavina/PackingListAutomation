package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Lo que el programa ha aprendido de una referencia: en qué cartón se
 * empaqueta y cuántas unidades le caben.
 *
 * No sale de ningún documento del cliente ni del taller —el taller manda una
 * sugerencia y a veces ni eso—: lo decide quien prepara el envío, y sin
 * recordarlo habría que teclearlo entero cada vez.
 */
@Entity
@Table(name = "memoria_caja_referencia")
@IdClass(MemoriaCajaReferenciaId.class)
public class MemoriaCajaReferencia {

    @Id
    @Column(name = "cliente", length = 60)
    private String cliente;

    @Id
    @Column(name = "referencia", length = 80)
    private String referencia;

    @Column(name = "medida_caja", length = 40, nullable = false)
    private String medidaCaja;

    @Column(name = "unidades_por_caja", nullable = false)
    private int unidadesPorCaja;

    /**
     * Lo que pesa la MERCANCÍA de una caja llena, sin el cartón. Nullable:
     * hasta que alguien pesa una caja no se sabe.
     *
     * Se guarda el neto y no el bruto a propósito. El neto es del artículo y
     * no cambia; el bruto lleva dentro la tara, que es un dato del almacén que
     * se corrige cada vez que se vuelve a pesar un cartón y que además cambia
     * entero si la referencia pasa a empaquetarse en otra caja. Guardando el
     * neto, el bruto se recompone siempre con la tara buena del momento.
     */
    @Column(name = "peso_neto_kg")
    private Double pesoNetoKg;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected MemoriaCajaReferencia() {
        // Constructor para JPA.
    }

    public MemoriaCajaReferencia(String cliente, String referencia,
                                 String medidaCaja, int unidadesPorCaja, Double pesoNetoKg) {
        this.cliente = cliente;
        this.referencia = referencia;
        this.medidaCaja = medidaCaja;
        this.unidadesPorCaja = unidadesPorCaja;
        this.pesoNetoKg = pesoNetoKg;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public String getCliente() {
        return cliente;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getMedidaCaja() {
        return medidaCaja;
    }

    public int getUnidadesPorCaja() {
        return unidadesPorCaja;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public Double getPesoNetoKg() {
        return pesoNetoKg;
    }

    /**
     * La última ejecución gana: si esta vez se ha usado otro cartón, ese es.
     *
     * El peso es la excepción: un peso nuevo lo sustituye, pero generar sin
     * pesar NO borra el que había. Pesar una caja cuesta ir al almacén con
     * ella, y perder ese dato porque el siguiente envío se generó con las
     * casillas de peso vacías sería tirar el trabajo de alguien.
     */
    public void actualizar(String medidaCaja, int unidadesPorCaja, Double pesoNetoKg) {
        this.medidaCaja = medidaCaja;
        this.unidadesPorCaja = unidadesPorCaja;
        if (pesoNetoKg != null) {
            this.pesoNetoKg = pesoNetoKg;
        }
        this.fechaActualizacion = LocalDateTime.now();
    }
}

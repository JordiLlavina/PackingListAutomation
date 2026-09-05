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

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected MemoriaCajaReferencia() {
        // Constructor para JPA.
    }

    public MemoriaCajaReferencia(String cliente, String referencia,
                                 String medidaCaja, int unidadesPorCaja) {
        this.cliente = cliente;
        this.referencia = referencia;
        this.medidaCaja = medidaCaja;
        this.unidadesPorCaja = unidadesPorCaja;
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

    /** La última ejecución gana: si esta vez se ha usado otro cartón, ese es. */
    public void actualizar(String medidaCaja, int unidadesPorCaja) {
        this.medidaCaja = medidaCaja;
        this.unidadesPorCaja = unidadesPorCaja;
        this.fechaActualizacion = LocalDateTime.now();
    }
}

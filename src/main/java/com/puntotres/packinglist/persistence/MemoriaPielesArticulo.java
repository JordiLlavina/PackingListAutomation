package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * El forro y las pieles de combinación de una referencia. Van por
 * referencia (modelo + piel) y no por piel porque dependen del BOLSO: dos
 * bolsos con la misma piel principal pueden llevar combinaciones distintas,
 * o ninguna. Cuatro columnas fijas porque la pantalla admite cuatro como
 * mucho y lo normal es una o ninguna.
 */
@Entity
@Table(name = "memoria_pieles_articulo")
@IdClass(MemoriaPielesArticuloId.class)
public class MemoriaPielesArticulo {

    @Id
    @Column(name = "cliente", length = 60)
    private String cliente;

    @Id
    @Column(name = "referencia", length = 80)
    private String referencia;

    @Column(name = "forro", length = 200)
    private String forro;

    @Column(name = "combinacion1", length = 200)
    private String combinacion1;

    @Column(name = "combinacion2", length = 200)
    private String combinacion2;

    @Column(name = "combinacion3", length = 200)
    private String combinacion3;

    @Column(name = "combinacion4", length = 200)
    private String combinacion4;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected MemoriaPielesArticulo() {
        // Constructor para JPA.
    }

    public MemoriaPielesArticulo(String cliente, String referencia, String forro,
                                 List<String> combinaciones) {
        this.cliente = cliente;
        this.referencia = referencia;
        actualizar(forro, combinaciones);
    }

    public String getForro() {
        return forro == null ? "" : forro;
    }

    /** Las combinaciones rellenas, en su orden. */
    public List<String> getCombinaciones() {
        return Stream.of(combinacion1, combinacion2, combinacion3, combinacion4)
                .filter(combinacion -> combinacion != null && !combinacion.isBlank())
                .toList();
    }

    /** La última generación gana entera: una combinación quitada es una combinación que ya no lleva. */
    public void actualizar(String forro, List<String> combinaciones) {
        this.forro = forro;
        this.combinacion1 = posicion(combinaciones, 0);
        this.combinacion2 = posicion(combinaciones, 1);
        this.combinacion3 = posicion(combinaciones, 2);
        this.combinacion4 = posicion(combinaciones, 3);
        this.fechaActualizacion = LocalDateTime.now();
    }

    private static String posicion(List<String> combinaciones, int indice) {
        return indice < combinaciones.size() ? combinaciones.get(indice) : null;
    }
}

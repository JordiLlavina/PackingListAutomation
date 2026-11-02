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
 * Un artículo del catálogo: un modelo en una piel, con la referencia entera
 * tal como la escribe el cliente ("ULL027.AL0103", "PXCBC-F67008"). Es el
 * nivel del cartón, las unidades por caja y el peso de la caja llena (antes
 * memoria_caja_referencia), y del forro y las combinaciones (antes
 * memoria_pieles_articulo): todo eso depende de la piel, o puede depender.
 *
 * {@code refPiel} es nulo cuando la regla del cliente no ha sabido partir la
 * referencia (las parciales de APC que llegan del taller, "F63023", o un
 * cliente genérico): ese artículo no hereda ni recibe propagación de ningún
 * hermano, porque no se sabe de qué modelo es de verdad.
 *
 * Cuatro columnas de combinación y no una tabla hija porque la pantalla
 * admite cuatro como mucho y lo normal es una o ninguna.
 */
@Entity
@Table(name = "articulo")
@IdClass(ArticuloId.class)
public class Articulo {

    @Id
    @Column(name = "cliente", length = 60)
    private String cliente;

    @Id
    @Column(name = "referencia", length = 80)
    private String referencia;

    @Column(name = "codigo_modelo", length = 80, nullable = false)
    private String codigoModelo;

    @Column(name = "ref_piel", length = 80)
    private String refPiel;

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

    @Column(name = "medida_caja", length = 40)
    private String medidaCaja;

    /** Nullable: hasta que alguien lo decide no se sabe, y eso es lo que bloquea la generación del taller. */
    @Column(name = "unidades_por_caja")
    private Integer unidadesPorCaja;

    /**
     * Lo que pesa la MERCANCÍA de una caja llena, sin el cartón. Se guarda el
     * neto y no el bruto: el neto es del artículo y no cambia, el bruto lleva
     * la tara dentro y cambia cada vez que se repesa un cartón.
     */
    @Column(name = "peso_neto_kg")
    private Double pesoNetoKg;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected Articulo() {
        // Constructor para JPA.
    }

    public Articulo(String cliente, String referencia, String codigoModelo, String refPiel) {
        this.cliente = cliente;
        this.referencia = referencia;
        this.codigoModelo = codigoModelo;
        this.refPiel = vacioANulo(refPiel);
        this.fechaActualizacion = LocalDateTime.now();
    }

    public String getCliente() {
        return cliente;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getCodigoModelo() {
        return codigoModelo;
    }

    /** La referencia de la piel, o "" si la regla del cliente no la separó. */
    public String getRefPiel() {
        return refPiel == null ? "" : refPiel;
    }

    public boolean tienePiel() {
        return refPiel != null;
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

    /** Las cuatro casillas tal cual, vacías incluidas: para la pantalla y para emparejar con sus colores. */
    public List<String> getCasillasCombinacion() {
        return Stream.of(combinacion1, combinacion2, combinacion3, combinacion4)
                .map(combinacion -> combinacion == null ? "" : combinacion)
                .toList();
    }

    public String getMedidaCaja() {
        return medidaCaja == null ? "" : medidaCaja;
    }

    public Integer getUnidadesPorCaja() {
        return unidadesPorCaja;
    }

    public Double getPesoNetoKg() {
        return pesoNetoKg;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    /**
     * Deja el artículo sin piel. Lo usa el borrado de una piel: la FK es
     * compuesta (cliente + ref_piel) y un ON DELETE SET NULL pondría a null
     * también el cliente, que es parte de la clave.
     */
    public void quitarPiel() {
        this.refPiel = null;
        tocar();
    }

    public void setForro(String forro) {
        this.forro = vacioANulo(forro);
        tocar();
    }

    /** La última lista gana entera: una combinación quitada es una combinación que ya no lleva. */
    public void setCombinaciones(List<String> combinaciones) {
        this.combinacion1 = posicion(combinaciones, 0);
        this.combinacion2 = posicion(combinaciones, 1);
        this.combinacion3 = posicion(combinaciones, 2);
        this.combinacion4 = posicion(combinaciones, 3);
        tocar();
    }

    /** Una casilla concreta (0..3), sin correr las demás. */
    public void setCombinacion(int indice, String valor) {
        String limpio = vacioANulo(valor);
        switch (indice) {
            case 0 -> combinacion1 = limpio;
            case 1 -> combinacion2 = limpio;
            case 2 -> combinacion3 = limpio;
            case 3 -> combinacion4 = limpio;
            default -> throw new IllegalArgumentException("Solo hay cuatro combinaciones");
        }
        tocar();
    }

    public void setMedidaCaja(String medidaCaja) {
        this.medidaCaja = vacioANulo(medidaCaja);
        tocar();
    }

    public void setUnidadesPorCaja(Integer unidadesPorCaja) {
        this.unidadesPorCaja = unidadesPorCaja;
        tocar();
    }

    public void setPesoNetoKg(Double pesoNetoKg) {
        this.pesoNetoKg = pesoNetoKg;
        tocar();
    }

    private void tocar() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }

    private static String posicion(List<String> valores, int indice) {
        return valores != null && indice < valores.size() ? vacioANulo(valores.get(indice)) : null;
    }
}

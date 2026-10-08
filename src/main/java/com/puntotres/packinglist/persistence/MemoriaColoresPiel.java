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
 * El color de cada piel de una referencia en uno de sus colores: la piel
 * principal, el forro y hasta cuatro combinaciones. Va por cliente +
 * referencia + código de color del bolso porque es de ESE bolso en ESE
 * color: el ULL027.AL0103 negro lleva la piel en negro y el crudo, en crudo.
 * Las combinaciones van en el orden de las de MemoriaPielesArticulo, que es
 * el mismo en que se guardan sus nombres.
 */
@Entity
@Table(name = "memoria_colores_piel")
@IdClass(MemoriaColoresPielId.class)
public class MemoriaColoresPiel {

    @Id
    @Column(name = "cliente", length = 60)
    private String cliente;

    @Id
    @Column(name = "referencia", length = 80)
    private String referencia;

    @Id
    @Column(name = "codigo_color", length = 40)
    private String codigoColor;

    @Column(name = "color_piel", length = 200)
    private String colorPiel;

    @Column(name = "color_forro", length = 200)
    private String colorForro;

    @Column(name = "color_combinacion1", length = 200)
    private String colorCombinacion1;

    @Column(name = "color_combinacion2", length = 200)
    private String colorCombinacion2;

    @Column(name = "color_combinacion3", length = 200)
    private String colorCombinacion3;

    @Column(name = "color_combinacion4", length = 200)
    private String colorCombinacion4;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected MemoriaColoresPiel() {
        // Constructor para JPA.
    }

    public MemoriaColoresPiel(String cliente, String referencia, String codigoColor, String colorPiel,
                              String colorForro, List<String> coloresCombinacion) {
        this.cliente = cliente;
        this.referencia = referencia;
        this.codigoColor = codigoColor;
        actualizar(colorPiel, colorForro, coloresCombinacion);
    }

    public String getColorPiel() {
        return colorPiel == null ? "" : colorPiel;
    }

    public String getColorForro() {
        return colorForro == null ? "" : colorForro;
    }

    /**
     * Los colores de las combinaciones, uno por combinación y vacíos
     * incluidos: van emparejados por posición con los nombres recordados,
     * así que no se puede quitar un hueco sin correr los de detrás.
     */
    public List<String> getColoresCombinacion() {
        List<String> colores = Stream.of(colorCombinacion1, colorCombinacion2, colorCombinacion3,
                        colorCombinacion4)
                .map(color -> color == null ? "" : color)
                .toList();
        int ultimo = colores.size();
        while (ultimo > 0 && colores.get(ultimo - 1).isEmpty()) {
            ultimo--;
        }
        return colores.subList(0, ultimo);
    }

    /** La última generación gana entera, como en el forro y las combinaciones. */
    public void actualizar(String colorPiel, String colorForro, List<String> coloresCombinacion) {
        this.colorPiel = colorPiel;
        this.colorForro = colorForro;
        this.colorCombinacion1 = posicion(coloresCombinacion, 0);
        this.colorCombinacion2 = posicion(coloresCombinacion, 1);
        this.colorCombinacion3 = posicion(coloresCombinacion, 2);
        this.colorCombinacion4 = posicion(coloresCombinacion, 3);
        this.fechaActualizacion = LocalDateTime.now();
    }

    private static String posicion(List<String> colores, int indice) {
        return indice < colores.size() ? colores.get(indice) : null;
    }
}

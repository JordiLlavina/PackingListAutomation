package com.puntotres.packinglist.service.corte;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Una fila de la pantalla de pieles con lo que se ha tecleado: el artículo
 * del pedido, los bolsos de cada color (editables; 0 = esa orden no sale),
 * los nombres de las pieles y qué foto del modelo va de principal.
 *
 * Mutable a propósito: vive en la sesión mientras se rellena la tabla, y cada
 * envío del formulario (añadir una combinación, generar) la actualiza.
 */
public class FilaCorte {

    private final ArticuloCorte articulo;
    private final int[] bolsos;
    private String nombrePiel = "";
    private String forro = "";
    private final List<String> combinaciones = new ArrayList<>();
    private int fotoPrincipal;

    public FilaCorte(ArticuloCorte articulo) {
        this.articulo = articulo;
        this.bolsos = articulo.colores().stream().mapToInt(ColorCorte::bolsos).toArray();
    }

    public ReferenciaCorte referencia() {
        return articulo.referencia();
    }

    /** Los colores con los bolsos que hay ahora, tecleados o del pedido. */
    public List<ColorCorte> colores() {
        return IntStream.range(0, bolsos.length)
                .mapToObj(i -> new ColorCorte(articulo.colores().get(i).color(), bolsos[i]))
                .toList();
    }

    public void setBolsos(int indiceColor, int valor) {
        if (indiceColor >= 0 && indiceColor < bolsos.length && valor >= 0) {
            bolsos[indiceColor] = valor;
        }
    }

    /** ¿Hay algo que cortar? Una fila sin bolsos no saca órdenes ni Word de fotos. */
    public boolean tieneBolsos() {
        return Arrays.stream(bolsos).anyMatch(valor -> valor > 0);
    }

    public String getNombrePiel() {
        return nombrePiel;
    }

    public void setNombrePiel(String nombrePiel) {
        this.nombrePiel = limpio(nombrePiel);
    }

    public String getForro() {
        return forro;
    }

    public void setForro(String forro) {
        this.forro = limpio(forro);
    }

    /** Las casillas de combinación tal como están, vacías incluidas: una por columna. */
    public List<String> getCombinaciones() {
        return List.copyOf(combinaciones);
    }

    public void setCombinaciones(List<String> combinaciones) {
        this.combinaciones.clear();
        if (combinaciones != null) {
            combinaciones.forEach(combinacion -> this.combinaciones.add(limpio(combinacion)));
        }
    }

    /** Índice en las fotos de su modelo, ordenadas por nombre. */
    public int getFotoPrincipal() {
        return fotoPrincipal;
    }

    public void setFotoPrincipal(int fotoPrincipal) {
        this.fotoPrincipal = Math.max(0, fotoPrincipal);
    }

    public PielesArticulo pieles() {
        return new PielesArticulo(nombrePiel, forro, combinaciones);
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }
}

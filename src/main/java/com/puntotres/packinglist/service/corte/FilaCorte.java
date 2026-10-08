package com.puntotres.packinglist.service.corte;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Una fila de la pantalla de pieles con lo que se ha tecleado: el artículo
 * del pedido, los bolsos de cada color (editables; 0 = esa orden no sale),
 * los nombres de las pieles, el color de cada piel en cada color del bolso y
 * qué foto del modelo va de principal.
 *
 * Los NOMBRES de las pieles son uno por fila: son del material y valen para
 * todos los colores. Los COLORES de piel son uno por color del bolso y por
 * piel, porque un bolso negro lleva la piel en negro y uno crudo en crudo. El
 * de la piel principal arranca con la descripción del color del bolso.
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
    private final String[] coloresPiel;
    private final String[] coloresForro;
    /** Por color del bolso, una casilla por columna de combinación, vacías incluidas. */
    private final List<List<String>> coloresCombinacion = new ArrayList<>();
    private int fotoPrincipal;
    private boolean incluida = true;

    public FilaCorte(ArticuloCorte articulo) {
        this.articulo = articulo;
        this.bolsos = articulo.colores().stream().mapToInt(ColorCorte::bolsos).toArray();
        this.coloresPiel = articulo.colores().stream().map(ColorCorte::descripcion).toArray(String[]::new);
        this.coloresForro = new String[bolsos.length];
        Arrays.fill(coloresForro, "");
        articulo.colores().forEach(color -> coloresCombinacion.add(new ArrayList<>()));
    }

    public ReferenciaCorte referencia() {
        return articulo.referencia();
    }

    /** El nombre del bolso si el pedido lo trae, o vacío. */
    public String nombreModelo() {
        return articulo.nombreModelo();
    }

    /**
     * La casilla de la tabla: una fila desmarcada no saca órdenes ni Word de
     * fotos, aunque tenga bolsos. Arranca marcada.
     */
    public boolean isIncluida() {
        return incluida;
    }

    public void setIncluida(boolean incluida) {
        this.incluida = incluida;
    }

    /** Los colores con los bolsos que hay ahora, tecleados o del pedido. */
    public List<ColorCorte> colores() {
        return IntStream.range(0, bolsos.length)
                .mapToObj(i -> articulo.colores().get(i).conBolsos(bolsos[i]))
                .toList();
    }

    public void setBolsos(int indiceColor, int valor) {
        if (existe(indiceColor) && valor >= 0) {
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

    public String getColorPiel(int indiceColor) {
        return existe(indiceColor) ? coloresPiel[indiceColor] : "";
    }

    public void setColorPiel(int indiceColor, String color) {
        if (existe(indiceColor)) {
            coloresPiel[indiceColor] = limpio(color);
        }
    }

    public String getColorForro(int indiceColor) {
        return existe(indiceColor) ? coloresForro[indiceColor] : "";
    }

    public void setColorForro(int indiceColor, String color) {
        if (existe(indiceColor)) {
            coloresForro[indiceColor] = limpio(color);
        }
    }

    /** Los colores de las combinaciones de un color del bolso, uno por columna, vacíos incluidos. */
    public List<String> getColoresCombinacion(int indiceColor) {
        return existe(indiceColor) ? List.copyOf(coloresCombinacion.get(indiceColor)) : List.of();
    }

    public void setColoresCombinacion(int indiceColor, List<String> colores) {
        if (!existe(indiceColor)) {
            return;
        }
        List<String> suyos = coloresCombinacion.get(indiceColor);
        suyos.clear();
        if (colores != null) {
            colores.forEach(color -> suyos.add(limpio(color)));
        }
    }

    /** Índice en las fotos de su modelo, ordenadas por nombre. */
    public int getFotoPrincipal() {
        return fotoPrincipal;
    }

    public void setFotoPrincipal(int fotoPrincipal) {
        this.fotoPrincipal = Math.max(0, fotoPrincipal);
    }

    /**
     * Las pieles de la orden de un color: los nombres de la fila con los
     * colores de ese color. Cada combinación se empareja con el color de SU
     * columna antes de quitar las vacías, para que una columna sin nombre no
     * le corra el color a la de al lado.
     */
    public PielesArticulo pieles(int indiceColor) {
        List<String> colores = getColoresCombinacion(indiceColor);
        List<PielCorte> conColor = IntStream.range(0, combinaciones.size())
                .mapToObj(i -> new PielCorte(combinaciones.get(i), i < colores.size() ? colores.get(i) : ""))
                .toList();
        return new PielesArticulo(new PielCorte(nombrePiel, getColorPiel(indiceColor)),
                new PielCorte(forro, getColorForro(indiceColor)), conColor);
    }

    private boolean existe(int indiceColor) {
        return indiceColor >= 0 && indiceColor < bolsos.length;
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }
}

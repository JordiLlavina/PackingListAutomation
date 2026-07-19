package com.puntotres.packinglist.model;

public class VoltadoErpLinea {
    private String article;           // referencia
    private String talla;             // talla
    private String colorCodi;         // 001, 002, 003...
    private String color;             // nombre color
    private int sistall;              // siempre 1
    private int sisgrup;              // siempre 1
    private int quantitat;            // cantidad total

    public VoltadoErpLinea(String article, String talla, String colorCodi, String color,
                           int sistall, int sisgrup, int quantitat) {
        this.article = article;
        this.talla = talla;
        this.colorCodi = colorCodi;
        this.color = color;
        this.sistall = sistall;
        this.sisgrup = sisgrup;
        this.quantitat = quantitat;
    }

    // Getters
    public String getArticle() { return article; }
    public String getTalla() { return talla; }
    public String getColorCodi() { return colorCodi; }
    public String getColor() { return color; }
    public int getSistall() { return sistall; }
    public int getSisgrup() { return sisgrup; }
    public int getQuantitat() { return quantitat; }
}

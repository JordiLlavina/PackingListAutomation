package com.puntotres.packinglist.model;

/**
 * Una línea del volcado de albarán para ICSuite: un artículo con su
 * cantidad total en el envío.
 *
 * Las columnas fijas del formato (% Dte 1, % Dte 2, Import Div.) no se
 * guardan aquí: son constantes del fichero y las escribe el
 * {@code VolcadoErpExcelBuilder}.
 */
public class VolcadoErpLinea {
    private int numeroLinea;          // Lin.: 1, 2, 3...
    private String article;           // referencia
    private String comanda;           // nº de comanda de ICSuite (puede ser null)
    private int quantitat;            // cantidad total
    private String uni;               // "U", o la talla si es cinturón
    private String color;             // código de color (columna extra, el ERP la ignora)
    private String talla;             // talla (columna extra, el ERP la ignora)

    public VolcadoErpLinea(int numeroLinea, String article, String comanda, int quantitat,
                           String uni, String color, String talla) {
        this.numeroLinea = numeroLinea;
        this.article = article;
        this.comanda = comanda;
        this.quantitat = quantitat;
        this.uni = uni;
        this.color = color;
        this.talla = talla;
    }

    // Getters
    public int getNumeroLinea() { return numeroLinea; }
    public String getArticle() { return article; }
    public String getComanda() { return comanda; }
    public int getQuantitat() { return quantitat; }
    public String getUni() { return uni; }
    public String getColor() { return color; }
    public String getTalla() { return talla; }
}

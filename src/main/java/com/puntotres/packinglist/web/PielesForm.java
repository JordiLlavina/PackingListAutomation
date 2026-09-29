package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que llega de la tabla de pieles: una fila por referencia, en el mismo
 * orden que CorteEnCurso.getFilas(). Los bolsos llegan como texto para poder
 * decir qué se tecleó mal en vez de perderlo en un error de conversión.
 */
public class PielesForm {

    private List<Fila> filas = new ArrayList<>();

    public List<Fila> getFilas() { return filas; }
    public void setFilas(List<Fila> filas) { this.filas = filas; }

    public static class Fila {

        private String nombrePiel;
        private String forro;
        private List<String> combinaciones = new ArrayList<>();
        private List<String> bolsos = new ArrayList<>();
        private Integer fotoPrincipal;
        /** null = no ha llegado ni la casilla ni su marcador "_": se deja como estaba. */
        private Boolean incluir;

        public String getNombrePiel() { return nombrePiel; }
        public void setNombrePiel(String nombrePiel) { this.nombrePiel = nombrePiel; }
        public String getForro() { return forro; }
        public void setForro(String forro) { this.forro = forro; }
        public List<String> getCombinaciones() { return combinaciones; }
        public void setCombinaciones(List<String> combinaciones) { this.combinaciones = combinaciones; }
        public List<String> getBolsos() { return bolsos; }
        public void setBolsos(List<String> bolsos) { this.bolsos = bolsos; }
        public Integer getFotoPrincipal() { return fotoPrincipal; }
        public void setFotoPrincipal(Integer fotoPrincipal) { this.fotoPrincipal = fotoPrincipal; }
        public Boolean getIncluir() { return incluir; }
        public void setIncluir(Boolean incluir) { this.incluir = incluir; }
    }
}

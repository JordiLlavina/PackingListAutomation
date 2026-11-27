package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.puntotres.packinglist.service.corte.LineaSmsRevisada;
import com.puntotres.packinglist.service.corte.PielCorte;

/**
 * La tabla de revisión del corte de SMS leído con IA, tal como la manda el
 * navegador ({@code filas[i].campo}). Las filas quitadas en pantalla dejan
 * huecos en la lista, que se saltan. La casilla de incluir va con el
 * marcador {@code _filas[i].incluir} de Spring: desmarcada llega false.
 */
public class RevisionSmsForm {

    private static final Pattern NUMERO_DELANTE = Pattern.compile("^\\s*(\\d+)");

    private List<Fila> filas = new ArrayList<>();

    public static class Fila {
        private Boolean incluir;
        private String hoja;
        private Integer fila;
        private String referencia;
        private String nombreModelo;
        private String codigoColor;
        private String descripcionColor;
        private String piel;
        private String colorPiel;
        private String forro;
        private String colorForro;
        private String muestras;
        private List<Combinacion> combinaciones = casillas();

        public Boolean getIncluir() { return incluir; }
        public void setIncluir(Boolean incluir) { this.incluir = incluir; }
        public String getHoja() { return hoja; }
        public void setHoja(String hoja) { this.hoja = hoja; }
        public Integer getFila() { return fila; }
        public void setFila(Integer fila) { this.fila = fila; }
        public String getReferencia() { return referencia; }
        public void setReferencia(String referencia) { this.referencia = referencia; }
        public String getNombreModelo() { return nombreModelo; }
        public void setNombreModelo(String nombreModelo) { this.nombreModelo = nombreModelo; }
        public String getCodigoColor() { return codigoColor; }
        public void setCodigoColor(String codigoColor) { this.codigoColor = codigoColor; }
        public String getDescripcionColor() { return descripcionColor; }
        public void setDescripcionColor(String descripcionColor) { this.descripcionColor = descripcionColor; }
        public String getPiel() { return piel; }
        public void setPiel(String piel) { this.piel = piel; }
        public String getColorPiel() { return colorPiel; }
        public void setColorPiel(String colorPiel) { this.colorPiel = colorPiel; }
        public String getForro() { return forro; }
        public void setForro(String forro) { this.forro = forro; }
        public String getColorForro() { return colorForro; }
        public void setColorForro(String colorForro) { this.colorForro = colorForro; }
        public String getMuestras() { return muestras; }
        public void setMuestras(String muestras) { this.muestras = muestras; }
        public List<Combinacion> getCombinaciones() { return combinaciones; }
        public void setCombinaciones(List<Combinacion> combinaciones) { this.combinaciones = combinaciones; }

        /** Cuántas combinaciones tienen nombre: lo que dice el desplegable de la fila. */
        public long getCombinacionesConNombre() {
            return combinaciones.stream().filter(c -> c != null && c.getNombre() != null
                    && !c.getNombre().isBlank()).count();
        }
    }

    public static class Combinacion {
        private String nombre = "";
        private String color = "";

        public Combinacion() {
        }

        Combinacion(String nombre, String color) {
            this.nombre = nombre;
            this.color = color;
        }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
    }

    public List<Fila> getFilas() {
        return filas;
    }

    public void setFilas(List<Fila> filas) {
        this.filas = filas;
    }

    static RevisionSmsForm desde(List<LineaSmsRevisada> lineas) {
        RevisionSmsForm formulario = new RevisionSmsForm();
        for (LineaSmsRevisada linea : lineas) {
            Fila fila = new Fila();
            fila.incluir = linea.incluir();
            fila.hoja = linea.hoja();
            fila.fila = linea.fila();
            fila.referencia = linea.referencia();
            fila.nombreModelo = linea.nombreModelo();
            fila.codigoColor = linea.codigoColor();
            fila.descripcionColor = linea.descripcionColor();
            fila.piel = linea.piel();
            fila.colorPiel = linea.colorPiel();
            fila.forro = linea.forro();
            fila.colorForro = linea.colorForro();
            fila.muestras = linea.muestras() == null ? "" : String.valueOf(linea.muestras());
            fila.combinaciones = casillas();
            for (int i = 0; i < linea.combinaciones().size() && i < 4; i++) {
                PielCorte piel = linea.combinaciones().get(i);
                fila.combinaciones.set(i, new Combinacion(piel.nombre(), piel.color()));
            }
            formulario.filas.add(fila);
        }
        return formulario;
    }

    List<LineaSmsRevisada> aLineas() {
        List<LineaSmsRevisada> lineas = new ArrayList<>();
        for (Fila fila : filas) {
            if (fila == null) {
                continue;
            }
            List<PielCorte> combinaciones = fila.combinaciones == null ? List.of() : fila.combinaciones.stream()
                    .filter(c -> c != null).map(c -> new PielCorte(c.getNombre(), c.getColor())).toList();
            lineas.add(new LineaSmsRevisada(Boolean.TRUE.equals(fila.incluir), fila.hoja, fila.fila,
                    fila.referencia, fila.nombreModelo, fila.codigoColor, fila.descripcionColor, fila.piel,
                    fila.colorPiel, fila.forro, fila.colorForro, combinaciones, numero(fila.muestras)));
        }
        return lineas;
    }

    /** «4», «4 SMS» → 4; vacío o sin número delante → null. */
    static Integer numero(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher numero = NUMERO_DELANTE.matcher(texto);
        return numero.find() ? Integer.valueOf(numero.group(1)) : null;
    }

    private static List<Combinacion> casillas() {
        List<Combinacion> casillas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            casillas.add(new Combinacion());
        }
        return casillas;
    }
}

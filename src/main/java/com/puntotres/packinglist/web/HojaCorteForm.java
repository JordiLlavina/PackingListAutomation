package com.puntotres.packinglist.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.puntotres.packinglist.model.DatosHojaCorte;

/**
 * El formulario de la Hoja de corte tal como lo manda el navegador: la
 * cabecera del modelo y {@code lineas[i].campo}, una por color, cada una con
 * cuatro casillas de combinación. Las líneas quitadas en pantalla dejan
 * huecos que se saltan.
 */
public class HojaCorteForm {

    private String cliente;
    private String temporada;
    private String referencia;
    private String modelo;
    private String nombreBolso;
    private String codigoModelo;
    private Long fotoId;
    private Long fotoHojaId;
    private List<Linea> lineas = new ArrayList<>();

    public static class Linea {
        private String codigoColor;
        private String descripcionColor;
        private String cantidad;
        private String piel;
        private String colorPiel;
        private String forro;
        private String colorForro;
        private List<Combinacion> combinaciones = casillas();

        public String getCodigoColor() { return codigoColor; }
        public void setCodigoColor(String codigoColor) { this.codigoColor = codigoColor; }
        public String getDescripcionColor() { return descripcionColor; }
        public void setDescripcionColor(String descripcionColor) { this.descripcionColor = descripcionColor; }
        public String getCantidad() { return cantidad; }
        public void setCantidad(String cantidad) { this.cantidad = cantidad; }
        public String getPiel() { return piel; }
        public void setPiel(String piel) { this.piel = piel; }
        public String getColorPiel() { return colorPiel; }
        public void setColorPiel(String colorPiel) { this.colorPiel = colorPiel; }
        public String getForro() { return forro; }
        public void setForro(String forro) { this.forro = forro; }
        public String getColorForro() { return colorForro; }
        public void setColorForro(String colorForro) { this.colorForro = colorForro; }
        public List<Combinacion> getCombinaciones() { return combinaciones; }
        public void setCombinaciones(List<Combinacion> combinaciones) { this.combinaciones = combinaciones; }

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

    /** El formulario de una hoja guardada, para importarla. */
    static HojaCorteForm desde(DatosHojaCorte datos) {
        HojaCorteForm formulario = new HojaCorteForm();
        formulario.cliente = datos.cliente();
        formulario.temporada = datos.temporada();
        formulario.referencia = datos.referencia();
        formulario.modelo = datos.modelo();
        formulario.nombreBolso = datos.nombreBolso();
        formulario.codigoModelo = datos.codigoModelo();
        for (DatosHojaCorte.Linea guardada : datos.lineas()) {
            Linea linea = new Linea();
            linea.codigoColor = guardada.codigoColor();
            linea.descripcionColor = guardada.descripcionColor();
            linea.cantidad = guardada.cantidad() == null ? "" : String.valueOf(guardada.cantidad());
            linea.piel = guardada.principal().nombre();
            linea.colorPiel = guardada.principal().color();
            linea.forro = guardada.forro().nombre();
            linea.colorForro = guardada.forro().color();
            for (int i = 0; i < guardada.combinaciones().size() && i < 4; i++) {
                DatosHojaCorte.Piel piel = guardada.combinaciones().get(i);
                linea.combinaciones.set(i, new Combinacion(piel.nombre(), piel.color()));
            }
            formulario.lineas.add(linea);
        }
        return formulario;
    }

    DatosHojaCorte aDatos(String nombreCliente) {
        List<DatosHojaCorte.Linea> datos = new ArrayList<>();
        for (Linea linea : lineas) {
            if (linea == null) {
                continue;
            }
            List<DatosHojaCorte.Piel> combinaciones = linea.combinaciones == null ? List.of()
                    : linea.combinaciones.stream().filter(c -> c != null)
                            .map(c -> new DatosHojaCorte.Piel(c.getNombre(), c.getColor())).toList();
            datos.add(new DatosHojaCorte.Linea(linea.codigoColor, linea.descripcionColor,
                    RevisionSmsForm.numero(linea.cantidad), new DatosHojaCorte.Piel(linea.piel, linea.colorPiel),
                    new DatosHojaCorte.Piel(linea.forro, linea.colorForro), combinaciones));
        }
        return new DatosHojaCorte(cliente, nombreCliente, temporada, referencia, modelo, nombreBolso, codigoModelo,
                LocalDate.now(), datos);
    }

    private static List<Combinacion> casillas() {
        List<Combinacion> casillas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            casillas.add(new Combinacion());
        }
        return casillas;
    }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }
    public String getTemporada() { return temporada; }
    public void setTemporada(String temporada) { this.temporada = temporada; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }
    public String getNombreBolso() { return nombreBolso; }
    public void setNombreBolso(String nombreBolso) { this.nombreBolso = nombreBolso; }
    public String getCodigoModelo() { return codigoModelo; }
    public void setCodigoModelo(String codigoModelo) { this.codigoModelo = codigoModelo; }
    public Long getFotoId() { return fotoId; }
    public void setFotoId(Long fotoId) { this.fotoId = fotoId; }
    public Long getFotoHojaId() { return fotoHojaId; }
    public void setFotoHojaId(Long fotoHojaId) { this.fotoHojaId = fotoHojaId; }
    public List<Linea> getLineas() { return lineas; }
    public void setLineas(List<Linea> lineas) { this.lineas = lineas; }
}

package com.puntotres.packinglist.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import org.springframework.format.annotation.DateTimeFormat;

import com.puntotres.packinglist.model.DatosHojaProduccion;
import com.puntotres.packinglist.model.DatosHojaProduccion.LineaProduccion;

/**
 * Lo que manda el formulario de Hojas de Producción al generar, y la
 * conversión a los {@link DatosHojaProduccion} que se imprimen y se guardan.
 *
 * El cliente sí va aquí (al revés que en la info de embalaje), porque no hay
 * pedido cargado del que sacarlo: es un desplegable del propio formulario y
 * el servidor comprueba que exista en el catálogo. El nombre del cliente no
 * viaja: lo pone el servidor.
 */
public class HojaProduccionForm {

    private String cliente;
    private String temporada;
    /** El modelo de la hoja (una hoja = un modelo): se rellena al elegir el artículo y se puede corregir. */
    private String modelo;
    /** El código de modelo del artículo elegido, con el que se busca la foto; no se teclea. */
    private String codigoModelo;
    /** La foto del catálogo que enseña la pantalla; no se guarda en la hoja. */
    private Long fotoId;
    /**
     * La hoja importada cuya foto SUBIDA se reutiliza (la foto va con la hoja,
     * no con el catálogo): si se genera desde una importada sin cambiar la
     * foto, la nueva hereda la suya.
     */
    private Long fotoHojaId;
    private String nombreBolso;
    private String taller;
    private Integer numero;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaSalida;
    private List<Linea> lineas = new ArrayList<>();

    /** Una línea de la tabla tal como llega del navegador. */
    public static class Linea {
        private Integer unidades;
        private String pielBase;
        private String colorBase;
        private String pielCombinacion;
        private String colorCombinacion;
        private String marcaHilo;
        private String numeroHilo;
        private String cmHilo;
        private String marcadoInterior;
        private String marcadoNombre;
        private String marcadoColor;
        private String marcadoTamano;
        private String fornitura;
        private String forro;
        private String tinta;

        public Linea() {
        }

        static Linea desde(LineaProduccion l) {
            Linea linea = new Linea();
            linea.unidades = l.unidades();
            linea.pielBase = l.pielBase();
            linea.colorBase = l.colorBase();
            linea.pielCombinacion = l.pielCombinacion();
            linea.colorCombinacion = l.colorCombinacion();
            linea.marcaHilo = l.marcaHilo();
            linea.numeroHilo = l.numeroHilo();
            linea.cmHilo = l.cmHilo();
            linea.marcadoInterior = l.marcadoInterior();
            linea.marcadoNombre = l.marcadoNombre();
            linea.marcadoColor = l.marcadoColor();
            linea.marcadoTamano = l.marcadoTamano();
            linea.fornitura = l.fornitura();
            linea.forro = l.forro();
            linea.tinta = l.tinta();
            return linea;
        }

        LineaProduccion aDatos() {
            return new LineaProduccion(unidades, recortar(pielBase), recortar(colorBase),
                    recortar(pielCombinacion), recortar(colorCombinacion), recortar(marcaHilo),
                    recortar(numeroHilo), recortar(cmHilo), recortar(marcadoInterior), recortar(marcadoNombre),
                    recortar(marcadoColor), recortar(marcadoTamano), recortar(fornitura), recortar(forro),
                    recortar(tinta));
        }

        public Integer getUnidades() { return unidades; }
        public void setUnidades(Integer unidades) { this.unidades = unidades; }
        public String getPielBase() { return pielBase; }
        public void setPielBase(String pielBase) { this.pielBase = pielBase; }
        public String getColorBase() { return colorBase; }
        public void setColorBase(String colorBase) { this.colorBase = colorBase; }
        public String getPielCombinacion() { return pielCombinacion; }
        public void setPielCombinacion(String pielCombinacion) { this.pielCombinacion = pielCombinacion; }
        public String getColorCombinacion() { return colorCombinacion; }
        public void setColorCombinacion(String colorCombinacion) { this.colorCombinacion = colorCombinacion; }
        public String getMarcaHilo() { return marcaHilo; }
        public void setMarcaHilo(String marcaHilo) { this.marcaHilo = marcaHilo; }
        public String getNumeroHilo() { return numeroHilo; }
        public void setNumeroHilo(String numeroHilo) { this.numeroHilo = numeroHilo; }
        public String getCmHilo() { return cmHilo; }
        public void setCmHilo(String cmHilo) { this.cmHilo = cmHilo; }
        public String getMarcadoInterior() { return marcadoInterior; }
        public void setMarcadoInterior(String marcadoInterior) { this.marcadoInterior = marcadoInterior; }
        public String getMarcadoNombre() { return marcadoNombre; }
        public void setMarcadoNombre(String marcadoNombre) { this.marcadoNombre = marcadoNombre; }
        public String getMarcadoColor() { return marcadoColor; }
        public void setMarcadoColor(String marcadoColor) { this.marcadoColor = marcadoColor; }
        public String getMarcadoTamano() { return marcadoTamano; }
        public void setMarcadoTamano(String marcadoTamano) { this.marcadoTamano = marcadoTamano; }
        public String getFornitura() { return fornitura; }
        public void setFornitura(String fornitura) { this.fornitura = fornitura; }
        public String getForro() { return forro; }
        public void setForro(String forro) { this.forro = forro; }
        public String getTinta() { return tinta; }
        public void setTinta(String tinta) { this.tinta = tinta; }
    }

    /** El formulario con los valores de una hoja guardada, para «Importar al formulario». */
    public static HojaProduccionForm desde(DatosHojaProduccion datos) {
        HojaProduccionForm form = new HojaProduccionForm();
        form.cliente = datos.cliente();
        form.temporada = datos.temporada();
        form.modelo = datos.modelo();
        form.codigoModelo = datos.codigoModelo();
        form.nombreBolso = datos.nombreBolso();
        form.taller = datos.taller();
        form.numero = datos.numero();
        form.fechaSalida = datos.fechaSalida();
        for (LineaProduccion linea : datos.lineas()) {
            form.lineas.add(Linea.desde(linea));
        }
        return form;
    }

    /**
     * Los datos de la hoja. Las líneas vacías se descartan (es la que se
     * añadió y no se usó). Un número vacío se resuelve con
     * {@code siguienteNumero}, el contador del archivo para el taller.
     */
    public DatosHojaProduccion aDatos(String nombreCliente, ToIntFunction<String> siguienteNumero) {
        List<LineaProduccion> datosLineas = new ArrayList<>();
        if (lineas != null) {
            for (Linea linea : lineas) {
                if (linea == null) {
                    continue;
                }
                LineaProduccion datos = linea.aDatos();
                if (!datos.estaVacia()) {
                    datosLineas.add(datos);
                }
            }
        }
        String tallerLimpio = recortar(taller);
        return new DatosHojaProduccion(recortar(cliente), nombreCliente, recortar(temporada), recortar(modelo),
                recortar(codigoModelo), recortar(nombreBolso),
                tallerLimpio, numero != null ? numero : siguienteNumero.applyAsInt(tallerLimpio), fechaSalida,
                datosLineas);
    }

    private static String recortar(String texto) {
        return texto == null ? "" : texto.trim();
    }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }
    public String getTemporada() { return temporada; }
    public void setTemporada(String temporada) { this.temporada = temporada; }
    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }
    public String getCodigoModelo() { return codigoModelo; }
    public void setCodigoModelo(String codigoModelo) { this.codigoModelo = codigoModelo; }
    public Long getFotoId() { return fotoId; }
    public void setFotoId(Long fotoId) { this.fotoId = fotoId; }
    public Long getFotoHojaId() { return fotoHojaId; }
    public void setFotoHojaId(Long fotoHojaId) { this.fotoHojaId = fotoHojaId; }
    public String getNombreBolso() { return nombreBolso; }
    public void setNombreBolso(String nombreBolso) { this.nombreBolso = nombreBolso; }
    public String getTaller() { return taller; }
    public void setTaller(String taller) { this.taller = taller; }
    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }
    public LocalDate getFechaSalida() { return fechaSalida; }
    public void setFechaSalida(LocalDate fechaSalida) { this.fechaSalida = fechaSalida; }
    public List<Linea> getLineas() { return lineas; }
    public void setLineas(List<Linea> lineas) { this.lineas = lineas; }
}

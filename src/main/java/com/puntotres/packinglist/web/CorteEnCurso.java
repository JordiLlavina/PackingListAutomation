package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.service.corte.ArticuloCorte;
import com.puntotres.packinglist.service.corte.ConversionFotos;
import com.puntotres.packinglist.service.corte.FilaCorte;
import com.puntotres.packinglist.service.corte.FotosTemporada;
import com.puntotres.packinglist.service.corte.PedidoCorte;
import com.puntotres.packinglist.service.corte.ResultadoCorte;

import jakarta.annotation.PreDestroy;

/**
 * Estado de los documentos del corte entre sus tres pantallas.
 *
 * Independiente de los demás flujos, como EtiquetasArticuloEnCurso. Las
 * fotos y los Word viven en un DIRECTORIO TEMPORAL de la sesión y no en
 * memoria —una temporada son cientos de megas de fotos—; se borra al empezar
 * otra carga, con "empezar de nuevo" y al caducar la sesión.
 */
@Component
@SessionScope
public class CorteEnCurso {

    /** Prefijo del directorio temporal de cada carga; por él lo reconoce LimpiezaDirectoriosCorte. */
    static final String PREFIJO_DIRECTORIO = "documentos-corte-";

    private String claveCliente;
    private String nombreCliente;
    private String temporada;
    private Path directorio;
    private FotosTemporada fotos;
    private ConversionFotos conversion;
    private final List<FilaCorte> filas = new ArrayList<>();
    private int columnasCombinacion;
    private final List<String> avisosCarga = new ArrayList<>();
    private ResultadoCorte resultado;

    public boolean tieneCarga() {
        return conversion != null && !filas.isEmpty();
    }

    /**
     * Sustituye lo que hubiera. Si había otra carga —dos pestañas que cargan
     * a la vez en la misma sesión, la segunda después de que la primera ya
     * pasara por reiniciar()—, su conversión se cancela y su directorio se
     * borra aquí: si no, se quedaban trabajando y ocupando disco sin dueño.
     */
    public synchronized void cargar(String claveCliente, String nombreCliente, String temporada,
                                    Path directorio, PedidoCorte pedido, FotosTemporada fotos,
                                    ConversionFotos conversion, List<String> avisos) {
        if (this.conversion != null && this.conversion != conversion) {
            this.conversion.cancelar();
        }
        if (this.directorio != null && !this.directorio.equals(directorio)) {
            borrar(this.directorio);
        }
        this.claveCliente = claveCliente;
        this.nombreCliente = nombreCliente;
        this.temporada = temporada;
        this.directorio = directorio;
        this.fotos = fotos;
        this.conversion = conversion;
        filas.clear();
        for (ArticuloCorte articulo : pedido.articulos()) {
            filas.add(new FilaCorte(articulo));
        }
        avisosCarga.clear();
        avisosCarga.addAll(avisos);
        columnasCombinacion = 0;
        resultado = null;
    }

    /** Tantas columnas como la fila que más combinaciones recordadas traiga. */
    public void ajustarColumnasALasFilas() {
        int maximo = filas.stream().mapToInt(fila -> fila.getCombinaciones().size()).max().orElse(0);
        columnasCombinacion = Math.min(MemoriaPieles.MAXIMO_COMBINACIONES,
                Math.max(columnasCombinacion, maximo));
    }

    public void anadirColumnaCombinacion() {
        if (columnasCombinacion < MemoriaPieles.MAXIMO_COMBINACIONES) {
            columnasCombinacion++;
        }
    }

    public synchronized void reiniciar() {
        if (conversion != null) {
            conversion.cancelar();
        }
        borrar(directorio);
        claveCliente = null;
        nombreCliente = null;
        temporada = null;
        directorio = null;
        fotos = null;
        conversion = null;
        filas.clear();
        columnasCombinacion = 0;
        avisosCarga.clear();
        resultado = null;
    }

    @PreDestroy
    public void alCerrarLaSesion() {
        reiniciar();
    }

    /** Borra un directorio con todo lo que tenga. Lo que no se deje borrar se queda en temporales. */
    static void borrar(Path directorio) {
        if (directorio == null || !Files.exists(directorio)) {
            return;
        }
        try (Stream<Path> todo = Files.walk(directorio)) {
            todo.sorted(Comparator.reverseOrder()).forEach(ruta -> {
                try {
                    Files.deleteIfExists(ruta);
                } catch (IOException e) {
                    // Un fichero abierto por otro proceso: lo limpiará el sistema.
                }
            });
        } catch (IOException e) {
            // Igual que arriba.
        }
    }

    public String getClaveCliente() { return claveCliente; }
    public String getNombreCliente() { return nombreCliente; }
    public String getTemporada() { return temporada; }
    public Path getDirectorio() { return directorio; }
    public FotosTemporada getFotos() { return fotos; }
    public ConversionFotos getConversion() { return conversion; }
    public List<FilaCorte> getFilas() { return filas; }
    public int getColumnasCombinacion() { return columnasCombinacion; }
    public List<String> getAvisosCarga() { return avisosCarga; }
    public ResultadoCorte getResultado() { return resultado; }
    public void setResultado(ResultadoCorte resultado) { this.resultado = resultado; }
}

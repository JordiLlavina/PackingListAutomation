package com.puntotres.packinglist.service.cargamodelos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.EstadoModelo;
import com.puntotres.packinglist.service.corte.ConversionFotos;

/**
 * Una carga de modelos en marcha: los modelos que se están revisando en la
 * batería y el directorio temporal donde viven sus fotos y sus fichas
 * técnicas. Vive en la sesión; nada de lo que hay aquí está en el catálogo
 * hasta que {@link CargadorModelos} carga los aceptados.
 */
public class LoteCarga {

    final OrigenCarga origen;
    /** El cliente elegido en el origen; con «Añadir modelo» cada modelo puede llevar el suyo. */
    final String cliente;
    /** La temporada guardada a la que se vinculan los modelos, o null. */
    Long temporadaId;
    final Path directorio;
    final List<ModeloEnCarga> modelos = new ArrayList<>();
    final List<String> avisos = new ArrayList<>();
    /** Las conversiones de fotos lanzadas: la de la carpeta y las de lo subido después. */
    final List<ConversionFotos> conversiones = new ArrayList<>();
    private int secuencia;

    LoteCarga(OrigenCarga origen, String cliente, Long temporadaId, Path directorio) {
        this.origen = origen;
        this.cliente = cliente;
        this.temporadaId = temporadaId;
        this.directorio = directorio;
    }

    public OrigenCarga origen() {
        return origen;
    }

    public String cliente() {
        return cliente;
    }

    public Path directorio() {
        return directorio;
    }

    public List<ModeloEnCarga> modelos() {
        return modelos;
    }

    public List<String> avisos() {
        return avisos;
    }

    public Optional<ModeloEnCarga> modelo(int id) {
        return modelos.stream().filter(modelo -> modelo.id == id).findFirst();
    }

    /** Un número que no se repite en el lote: ids de modelo, de foto y de ficha, y nombres de fichero. */
    int siguiente() {
        return ++secuencia;
    }

    public int fotosTotal() {
        return conversiones.stream().mapToInt(ConversionFotos::total).sum();
    }

    public int fotosHechas() {
        return conversiones.stream().mapToInt(ConversionFotos::hechas).sum();
    }

    /** Todas las fotos subidas están ya convertidas (o no se han podido convertir). */
    public boolean fotosListas() {
        return conversiones.stream().allMatch(ConversionFotos::terminada);
    }

    public long cuantos(EstadoModelo estado) {
        return modelos.stream().filter(modelo -> modelo.estado == estado).count();
    }

    /** Para lo que quede por convertir: al empezar otra carga o al caducar la sesión. */
    public void cancelar() {
        conversiones.forEach(ConversionFotos::cancelar);
    }
}

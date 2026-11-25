package com.puntotres.packinglist.service.cargamodelos;

import java.util.List;

/**
 * El panel de un modelo tal como lo manda la pantalla al cambiar cualquier
 * campo: el panel ENTERO, no el campo suelto. {@link ConstructorLote#aplicar}
 * lo compara con lo que había y apunta como «tocado» cada campo que cambia.
 *
 * Una lista nula significa «no tocar»; una piel o un color que no vienen se
 * quitan (salvo los que ya existen en el catálogo, que aquí no se borran).
 * {@code fotos} dice el orden de la tira y cuáles están seleccionadas.
 */
public record EdicionModelo(String cliente, String codigo, String nombrePrototipo, Long temporadaId, String nombre,
                            String tipo, String etapa, List<EdicionPiel> pieles, List<EdicionFoto> fotos) {

    public record EdicionPiel(String referencia, String nombrePiel, String forro, List<String> combinaciones,
                              String medidaCaja, String unidadesPorCaja, String pesoNetoUnidadKg,
                              List<EdicionColor> colores) {
    }

    public record EdicionColor(String codigo, String descripcion, String colorPiel, String colorForro,
                               List<String> coloresCombinacion) {
    }

    public record EdicionFoto(String id, boolean seleccionada) {
    }
}

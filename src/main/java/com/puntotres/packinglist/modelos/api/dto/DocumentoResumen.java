package com.puntotres.packinglist.modelos.api.dto;

import java.time.LocalDateTime;

/**
 * Un documento del modelo (ficha técnica, PDF del cliente) sin sus bytes:
 * lo que se enseña en la ficha. Sale de {@code documento_modelo} con una
 * consulta que no carga los bytes.
 */
public record DocumentoResumen(Long id, String nombre, String mime, long tamano, LocalDateTime fecha) {
}

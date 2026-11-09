package com.puntotres.packinglist.modelos.api.dto;

import java.time.LocalDateTime;

/**
 * Un documento del modelo (ficha técnica, PDF del cliente) sin sus bytes.
 * La ficha lo lista vacío hasta que existan los documentos del modelo; sus
 * campos son las columnas de {@code documento_modelo} que se enseñan.
 */
public record DocumentoResumen(Long id, String nombre, String mime, long tamano, LocalDateTime fecha) {
}

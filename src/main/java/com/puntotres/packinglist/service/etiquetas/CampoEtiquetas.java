package com.puntotres.packinglist.service.etiquetas;

/**
 * Un input del Paso 2 de etiquetas que el generador de un cliente pide al
 * usuario ANTES de generar (siempre un archivo .xlsx en esta fase; si un
 * cliente futuro necesita un texto/código, se ampliará con un tipo).
 *
 * nombre: name del input HTML y clave del mapa de archivos.
 * titulo: etiqueta visible, ej. "Introducir excel del pedido de AMI".
 */
public record CampoEtiquetas(String nombre, String titulo) {
}

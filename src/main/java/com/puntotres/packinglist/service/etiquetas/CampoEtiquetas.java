package com.puntotres.packinglist.service.etiquetas;

/**
 * Un input del Paso 2 de etiquetas que el generador de un cliente pide al
 * usuario ANTES de generar (siempre un archivo .xlsx en esta fase; si un
 * cliente futuro necesita un texto/código, se ampliará con un tipo).
 *
 * nombre: name del input HTML y clave del mapa de archivos.
 * titulo: etiqueta visible, ej. "Introducir excel del pedido de AMI".
 * esPedidoCliente: es EL excel de pedido de la temporada, el mismo que se
 *   sube en la pantalla de entrada; si ya está en sesión no hace falta
 *   volver a subirlo. Se declara explícitamente en vez de deducirlo del
 *   nombre del input porque un generador futuro puede pedir dos ficheros.
 */
public record CampoEtiquetas(String nombre, String titulo, boolean esPedidoCliente) {
}

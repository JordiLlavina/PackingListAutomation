package com.puntotres.packinglist.service.etiquetas;

/**
 * Un archivo que el generador de etiquetas de un cliente necesita para poder
 * generar (siempre un .xlsx en esta fase; si un cliente futuro necesita un
 * texto/código, se ampliará con un tipo).
 *
 * Ya no hay pantalla que los pida: el controlador los resuelve de la sesión
 * al generar, y lo que no pueda resolver sale como aviso en resultados.
 *
 * nombre: clave del mapa de archivos que recibe {@code generar}.
 * titulo: cómo se nombra el archivo en el aviso de "Falta el ...", así que
 *   va en minúscula y encaja dentro de una frase.
 * esPedidoCliente: es EL excel de pedido de la temporada, el que se sube en
 *   la pantalla de entrada. Se declara explícitamente en vez de deducirlo
 *   del nombre porque un generador futuro puede pedir dos ficheros.
 */
public record CampoEtiquetas(String nombre, String titulo, boolean esPedidoCliente) {
}

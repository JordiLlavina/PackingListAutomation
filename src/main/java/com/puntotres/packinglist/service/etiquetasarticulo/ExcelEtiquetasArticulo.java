package com.puntotres.packinglist.service.etiquetasarticulo;

/**
 * Un excel de etiquetas de artículo generado.
 *
 * No se reutiliza ExcelGenerado: su vocabulario es de packing list (destino,
 * referencia, color, cajasPendientes) y aquí no aplica ninguno de sus
 * campos. descripcion es el texto que ve el usuario en la pantalla de
 * resultados, ej. "Bolsos · MOROCCO · 46 hojas".
 */
public record ExcelEtiquetasArticulo(String descripcion, String nombreFichero,
                                     byte[] contenido) {
}

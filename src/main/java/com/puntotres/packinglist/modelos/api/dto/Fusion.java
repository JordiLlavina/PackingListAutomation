package com.puntotres.packinglist.modelos.api.dto;

/**
 * Lo que pasaría al juntar dos modelos, para decirlo antes de hacerlo (no se
 * puede deshacer): quién sobrevive, quién desaparece y cuánto se lleva el que
 * desaparece al superviviente.
 *
 * @param superviviente  el de menor id, el que se queda
 * @param absorbido      el que se borra y deja su id apuntado en el histórico
 *                       del superviviente; null si no hay nada que juntar (la
 *                       referencia está libre)
 * @param articulos      los artículos del absorbido
 * @param fotos          sus fotos
 * @param modificaciones sus modificaciones
 * @param documentos     sus documentos; 0 hasta que existan los documentos del modelo
 */
public record Fusion(Long superviviente, Long absorbido, int articulos, int fotos, int modificaciones,
                     int documentos) {
}

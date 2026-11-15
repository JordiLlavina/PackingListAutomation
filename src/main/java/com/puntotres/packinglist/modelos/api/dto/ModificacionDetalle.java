package com.puntotres.packinglist.modelos.api.dto;

import java.util.List;

/**
 * Todo lo que enseña una modificación abierta.
 *
 * @param motivoDescarte por qué se descartó; null si no se ha descartado
 * @param mensajes       solo el cuerpo, el primer mensaje: ya no hay respuestas (las dudas van
 *                       por teléfono). Es una lista para no cambiar la forma del JSON.
 */
public record ModificacionDetalle(ModificacionResumen resumen, String motivoDescarte, List<MensajeVista> mensajes) {
}

package com.puntotres.packinglist.modelos.api.dto;

import java.util.List;

/**
 * Todo lo que enseña una modificación abierta.
 *
 * @param motivoDescarte por qué se descartó; null si no se ha descartado
 * @param mensajes       del primero (el cuerpo) al último
 */
public record ModificacionDetalle(ModificacionResumen resumen, String motivoDescarte, List<MensajeVista> mensajes) {
}

package com.puntotres.packinglist.modelos.api.dto;

import java.util.List;

import com.puntotres.packinglist.modelos.service.Documentos;

/**
 * Lo que contesta crear una modificación: el resumen de la creada y, si el
 * despacho marcó «Guardar también en el modelo», lo que pasó con cada
 * documento que se copió al modelo ({@code documentosGuardados}: con el lote
 * y las candidatas si era un PDF, para elegir cuáles son fotos del modelo).
 *
 * Una copia al modelo que falla nunca deshace la orden ni se calla: va en
 * {@code avisos}, una línea por documento. Ambas listas van vacías cuando no
 * hay nada que decir (siempre, para el taller).
 */
public record ModificacionCreada(ModificacionResumen modificacion, List<Documentos.Subida> documentosGuardados,
                                 List<String> avisos) {
}

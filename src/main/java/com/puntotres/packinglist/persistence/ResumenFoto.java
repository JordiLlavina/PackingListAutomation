package com.puntotres.packinglist.persistence;

/**
 * Una foto del catálogo SIN sus bytes: lo que hace falta para listarla en la
 * pantalla o repartir las de una temporada en el corte. Va aparte de la
 * entidad por el mismo motivo que {@link ResumenTemporada}: listar un cliente
 * no puede traerse del disco todas sus fotos.
 */
public record ResumenFoto(Long id, String codigoModelo, String nombreFichero, boolean principal, int orden) {
}

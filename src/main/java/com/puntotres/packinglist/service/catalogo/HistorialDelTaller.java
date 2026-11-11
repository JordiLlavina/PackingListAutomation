package com.puntotres.packinglist.service.catalogo;

/**
 * Lo que el catálogo necesita saber de la app del taller antes de borrar un
 * modelo: si tiene historial (modificaciones o documentos) que no se puede
 * perder. Es un puerto: lo declara el catálogo y lo implementa el paquete
 * {@code modelos} ({@code HistorialDelTallerJpa}), para que el catálogo no
 * dependa de la app del taller.
 *
 * Sin implementación (un contexto sin la app del taller) ningún modelo tiene
 * historial. La base de datos es la segunda red: desde la V5 rechaza borrar
 * un modelo con modificaciones o documentos.
 */
@FunctionalInterface
public interface HistorialDelTaller {

    /** Verdadero si el modelo tiene alguna modificación o algún documento de la app del taller. */
    boolean tieneHistorial(Long modeloId);
}

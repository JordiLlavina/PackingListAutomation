package com.puntotres.packinglist.modelos.service;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.modelos.persistence.DocumentoModeloRepository;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.service.catalogo.HistorialDelTaller;

/**
 * El historial del taller de un modelo son sus modificaciones y sus
 * documentos: lo que el catálogo no debe borrar desde sus pantallas viejas.
 */
@Service
public class HistorialDelTallerJpa implements HistorialDelTaller {

    private final ModificacionRepository modificaciones;
    private final DocumentoModeloRepository documentos;

    public HistorialDelTallerJpa(ModificacionRepository modificaciones, DocumentoModeloRepository documentos) {
        this.modificaciones = modificaciones;
        this.documentos = documentos;
    }

    @Override
    public boolean tieneHistorial(Long modeloId) {
        return modeloId != null
                && (modificaciones.existsByModeloId(modeloId) || documentos.existsByModeloId(modeloId));
    }
}

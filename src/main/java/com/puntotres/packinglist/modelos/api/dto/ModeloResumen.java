package com.puntotres.packinglist.modelos.api.dto;

import java.time.LocalDateTime;

import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.TipoArticulo;

/**
 * Un modelo tal como sale en una lista o en la cabecera de su ficha.
 *
 * @param referencia      lo que se enseña: el código o, si no lo tiene, el
 *                        nombre de prototipo ({@link Modelo#nombreVisible()})
 * @param fotoPrincipalId la foto que lo representa; null si no tiene ninguna
 */
public record ModeloResumen(Long id, String cliente, String codigo, String nombrePrototipo, String referencia,
                            String nombre, TipoArticulo tipo, Etapa etapa, Long fotoPrincipalId,
                            LocalDateTime etiquetaImpresaEn, LocalDateTime fechaEtapa) {

    public static ModeloResumen de(Modelo modelo, Long fotoPrincipalId) {
        return new ModeloResumen(modelo.getId(), modelo.getCliente(), modelo.getCodigo(),
                modelo.getNombrePrototipo(), modelo.nombreVisible(), modelo.getNombre(), modelo.getTipo(),
                modelo.getEtapa(), fotoPrincipalId, modelo.getEtiquetaImpresaEn(), modelo.getFechaEtapa());
    }
}

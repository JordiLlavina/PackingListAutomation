package com.puntotres.packinglist.service;

import java.io.IOException;
import java.util.List;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

/**
 * Genera los packing lists de una destinación para un tipo de plantilla
 * concreto. Cada implementación decide su propia granularidad de fichero
 * (AMI: uno por referencia+color; APC/genérica: uno por destino) y qué
 * datos de {@link ClienteConfig} necesita.
 *
 * {@link PackingListGenerationService} despacha a la implementación
 * correcta según {@link ClienteConfig#getPlantilla()}, sin conocer nada
 * de plantillas Excel concretas.
 */
public interface GeneradorPackingListCliente {

    /** Tipo de plantilla que esta implementación sabe generar. */
    TipoPlantilla tipo();

    List<ExcelGenerado> generar(DestinoData destino, List<PaletData> palets,
                                DatosEnvio envio, ClienteConfig cliente) throws IOException;
}

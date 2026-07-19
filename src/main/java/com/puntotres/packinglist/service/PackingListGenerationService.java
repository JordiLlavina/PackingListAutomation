package com.puntotres.packinglist.service;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

/**
 * Despacha la generación de packing lists de una destinación al
 * {@link GeneradorPackingListCliente} que corresponda según la plantilla
 * del cliente (AMI/APC/genérica). No conoce nada de Excel: solo elige
 * la implementación registrada para ese tipo.
 *
 * Añadir un cliente nuevo de plantilla ya soportada no toca esta clase
 * (solo el catálogo de {@code application.yml}); añadir un tipo de
 * plantilla nuevo requiere una implementación nueva de
 * {@link GeneradorPackingListCliente} con su tipo correspondiente.
 */
@Service
public class PackingListGenerationService {

    private final Map<TipoPlantilla, GeneradorPackingListCliente> generadoresPorTipo;

    public PackingListGenerationService(List<GeneradorPackingListCliente> generadores) {
        this.generadoresPorTipo = new EnumMap<>(TipoPlantilla.class);
        for (GeneradorPackingListCliente generador : generadores) {
            generadoresPorTipo.put(generador.tipo(), generador);
        }
    }

    public List<ExcelGenerado> generar(DestinoData destino, List<PaletData> palets,
                                       DatosEnvio envio, ClienteConfig cliente) throws IOException {
        GeneradorPackingListCliente generador = generadoresPorTipo.get(cliente.getPlantilla());
        if (generador == null) {
            throw new IllegalStateException(
                    "No hay ningún generador registrado para la plantilla " + cliente.getPlantilla());
        }
        return generador.generar(destino, palets, envio, cliente);
    }
}

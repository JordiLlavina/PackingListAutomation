package com.puntotres.packinglist.service.etiquetas;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.service.EnvioImportado;

/**
 * Estrategia de generación de etiquetas de caja de un cliente concreto
 * (análoga a GeneradorPackingListCliente para los packing lists).
 *
 * Cada implementación sabe qué destinaciones soporta, qué datos estáticos
 * usar por destinación y qué archivos extra pedir al usuario en el Paso 2
 * del asistente. Las etiquetas de palet y la "etiqueta de etiqueta" son
 * funcionalidades futuras y NO forman parte de este contrato.
 */
public interface GeneradorEtiquetasCliente {

    /** Clave del cliente en el catálogo de application.yml (ej. "AMI"). */
    String claveCliente();

    /** ¿Este generador reconoce esta destinación del JSON? */
    boolean soportaDestino(String nombreDestino);

    /**
     * Archivos que hay que pedir al usuario para estas destinaciones
     * (Paso 2). Puede depender de qué destinaciones contenga el envío.
     */
    List<CampoEtiquetas> camposRequeridos(List<DestinoData> destinos);

    /**
     * Genera un excel de etiquetas por destinación soportada. Cada destino
     * llega con sus palets (los necesitan las etiquetas de palet; los
     * generadores sin ellas los ignoran). archivos: contenido de cada
     * CampoEtiquetas subido, indexado por su nombre.
     */
    ResultadoEtiquetas generar(List<EnvioImportado.DestinoImportado> destinos, DatosEnvio envio,
                               Map<String, byte[]> archivos) throws IOException;
}

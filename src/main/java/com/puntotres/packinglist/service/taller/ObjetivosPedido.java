package com.puntotres.packinglist.service.taller;

import java.util.List;

/**
 * De dónde sale la cantidad que hay que enviar de cada artículo y a qué
 * destinación.
 *
 * Se despacha por CLAVE DE CLIENTE y no por {@code TipoPlantilla}, igual que
 * los generadores de etiquetas: de dónde sale la cantidad pedida depende del
 * fichero que manda ese cliente concreto, no de la plantilla de Excel que se
 * le imprime. Dos clientes con la misma plantilla pueden mandar pedidos que
 * no se parecen en nada.
 *
 * Un cliente sin implementación no bloquea nada: se toma como objetivo lo que
 * ha llegado del taller y el usuario lo ajusta a mano.
 */
public interface ObjetivosPedido {

    /** Clave del cliente en el catálogo de application.yml. */
    String clienteSoportado();

    /**
     * Nunca lanza por datos que un humano pueda arreglar: lo que no se puede
     * resolver sale como aviso o como bloqueo dentro del resultado.
     */
    ResultadoObjetivos objetivosPara(List<LineaTaller> lineas, byte[] excelPedido);
}

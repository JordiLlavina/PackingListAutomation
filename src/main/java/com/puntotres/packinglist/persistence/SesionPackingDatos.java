package com.puntotres.packinglist.persistence;

import java.util.List;

import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

/**
 * El envío de una sesión guardada, tal como va a la columna {@code datos}.
 *
 * Lleva lo que NO se puede volver a calcular: la cabecera, las cajas y los
 * palets con todo lo tecleado, y los avisos de la importación y de los
 * palets, que salieron de un fichero que ya no está. Los avisos de pesos y la
 * lista de cajas sin palet no van: se rehacen al cargar leyendo las cajas.
 * Los excels generados tampoco: se regeneran con el botón de siempre.
 *
 * @param deTaller         el envío lo repartió el programa desde el packing
 *                         del taller: la revisión ofrece el Packing Puntotres
 * @param filasDesplegadas pares (destino, índice de la primera caja) de las
 *                         filas compactadas que estaban desplegadas
 */
public record SesionPackingDatos(DatosEnvio cabecera, List<Destino> destinos,
                                 List<String> avisosImportacion, List<String> avisosPalets,
                                 boolean deTaller, List<int[]> filasDesplegadas) {

    /** Una destinación con sus cajas y sus palets. */
    public record Destino(DestinoData destino, List<PaletData> palets) {
    }
}

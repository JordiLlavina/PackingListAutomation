package com.puntotres.packinglist.modelos.service;

import java.util.Map;

import com.puntotres.packinglist.modelos.persistence.DestinoAplicacion;

/**
 * Cómo se llama en pantalla cada destino de aplicación de un aviso. Una sola
 * copia para el desplegable y para el detalle de una modificación: con dos,
 * un destino renombrado en una se quedaría con el nombre viejo en la otra.
 */
public final class TextosDestino {

    private static final Map<String, String> TEXTOS = Map.of(
            "ICSUITE", "ICSUITE",
            "EXCEL_ESCANDALLOS", "Excel de escandallos");

    private TextosDestino() {
    }

    /** El texto del destino con esa clave; uno desconocido se enseña con su clave. */
    public static String texto(String clave) {
        return TEXTOS.getOrDefault(clave, clave);
    }

    public static String texto(DestinoAplicacion destino) {
        return texto(destino.name());
    }
}

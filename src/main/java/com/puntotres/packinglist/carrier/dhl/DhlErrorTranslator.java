package com.puntotres.packinglist.carrier.dhl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.carrier.dhl.dto.DhlErrorResponse;

/**
 * Convierte una respuesta de error de DHL en un texto para el usuario: código,
 * título, detalle y una línea por cada additionalDetails. Sin la ruta interna
 * (instance) ni trazas.
 */
public class DhlErrorTranslator {

    private static final int MAXIMO = 1500;

    private final ObjectMapper json;

    public DhlErrorTranslator(ObjectMapper json) {
        this.json = json.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String traducir(int status, String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) return "DHL (" + status + "): sin detalle";
        try {
            DhlErrorResponse error = json.readValue(cuerpo, DhlErrorResponse.class);
            StringBuilder sb = new StringBuilder("DHL (").append(status).append(")");
            if (error.title() != null) sb.append(" ").append(error.title());
            sb.append(":");
            if (error.detail() != null) sb.append(" ").append(error.detail());
            else if (error.message() != null) sb.append(" ").append(error.message());
            if (error.additionalDetails() != null) {
                for (String detalle : error.additionalDetails()) sb.append("\n- ").append(detalle);
            }
            return recortar(sb.toString());
        } catch (Exception noEsJson) {
            return recortar("DHL (" + status + "): " + cuerpo.trim());
        }
    }

    private static String recortar(String texto) {
        return texto.length() <= MAXIMO ? texto : texto.substring(0, MAXIMO) + "…";
    }
}

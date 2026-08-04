package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.DestinoClienteConfig;

class LivraisonCodeTest {

    private static DestinoClienteConfig destino(String abreviatura) {
        DestinoClienteConfig config = new DestinoClienteConfig();
        config.setAbreviatura(abreviatura);
        return config;
    }

    @Test
    void componeElCodigoConLaEstructuraDelCliente() {
        List<String> avisos = new ArrayList<>();

        String codigo = LivraisonCode.generar("WHOLESALE", destino("WH"), "28/04/2026", avisos);

        assertEquals("PUN20260428WH1", codigo);
        assertTrue(avisos.isEmpty());
    }

    @Test
    void sinAbreviaturaUsaElNombreEnMayusculasSinEspaciosYAvisa() {
        List<String> avisos = new ArrayList<>();

        String codigo = LivraisonCode.generar("D. USA", destino(null), "17/07/2026", avisos);

        assertEquals("PUN20260717D.USA1", codigo);
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).contains("D. USA"));
    }

    @Test
    void unaFechaIlegibleNoRompeElEnvio() {
        List<String> avisos = new ArrayList<>();

        String codigo = LivraisonCode.generar("WHOLESALE", destino("WH"), "no es fecha", avisos);

        assertEquals("PUNWH1", codigo);
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).contains("fecha de envío"));
    }

    @Test
    void fechaNulaSeTrataComoIlegible() {
        List<String> avisos = new ArrayList<>();

        assertEquals("PUNWH1", LivraisonCode.generar("WHOLESALE", destino("WH"), null, avisos));
        assertEquals(1, avisos.size());
    }
}

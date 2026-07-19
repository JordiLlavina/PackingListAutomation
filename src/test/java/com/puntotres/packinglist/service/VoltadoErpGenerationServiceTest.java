package com.puntotres.packinglist.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.puntotres.packinglist.model.*;
import java.util.*;

public class VoltadoErpGenerationServiceTest {

    @Test
    public void testGeneraVoltadoErpConArticulosAgrupados() {
        VoltadoErpGenerationService service = new VoltadoErpGenerationService();

        List<CajaData> cajas = new ArrayList<>();
        cajas.add(new CajaData("REF001", "001", "80", 50, null, null));
        cajas.add(new CajaData("REF001", "001", "80", 30, null, null));
        cajas.add(new CajaData("REF001", "002", "90", 20, null, null));

        DatosEnvio envio = new DatosEnvio();
        envio.setNumeroFactura("FA-26-1189");

        VoltadoErpData resultado = service.generar(cajas, envio);

        assertNotNull(resultado);
        assertEquals(2, resultado.getTotalLineas());  // 2 líneas únicas (REF001-80 y REF001-90)
        assertEquals("Volcado_ERP_FA-26-1189.xlsx", resultado.getNombreFichero());
    }

    @Test
    public void testGeneraColorCodiSecuencial() {
        VoltadoErpGenerationService service = new VoltadoErpGenerationService();

        List<CajaData> cajas = new ArrayList<>();
        cajas.add(new CajaData("REF001", "NOIR", "80", 50, null, null));
        cajas.add(new CajaData("REF001", "BLEU", "80", 30, null, null));
        cajas.add(new CajaData("REF001", "NOIR", "90", 20, null, null));

        DatosEnvio envio = new DatosEnvio();
        envio.setNumeroFactura("TEST");

        VoltadoErpData resultado = service.generar(cajas, envio);

        // NOIR aparece primero → 001
        // BLEU aparece segundo → 002
        // NOIR aparece de nuevo → 001
        VoltadoErpLinea linea1 = resultado.getLineas().get(0);
        VoltadoErpLinea linea2 = resultado.getLineas().get(1);

        assertEquals("001", linea1.getColorCodi());
        assertEquals("002", linea2.getColorCodi());
    }
}

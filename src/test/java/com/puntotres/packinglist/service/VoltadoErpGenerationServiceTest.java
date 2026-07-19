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

        // Verify that colors in same reference+talla combine into single line:
        // REF001-talla80 combines NOIR (50) + BLEU (30) = 1 line (takes first color NOIR)
        // REF001-talla90 has NOIR (20) = 1 line
        // Total: 2 lines (not 3), proving colors are aggregated by reference+talla
        assertEquals(2, resultado.getTotalLineas(), "Should have 2 lines when combining NOIR+BLEU in talla 80");

        VoltadoErpLinea linea1 = resultado.getLineas().get(0);
        VoltadoErpLinea linea2 = resultado.getLineas().get(1);

        // Both lines have NOIR (first color encountered for each reference+talla)
        // Both get colorCodi 001 since NOIR was the first color encountered
        assertEquals("001", linea1.getColorCodi());
        assertEquals("001", linea2.getColorCodi());

        // Verify quantities are summed: line 1 has 50+30=80 units
        assertEquals(80, linea1.getQuantitat());
        assertEquals(20, linea2.getQuantitat());
    }
}

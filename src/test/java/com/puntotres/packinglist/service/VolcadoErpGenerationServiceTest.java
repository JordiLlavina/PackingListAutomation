package com.puntotres.packinglist.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.puntotres.packinglist.model.*;
import java.util.*;

public class VolcadoErpGenerationServiceTest {

    @Test
    public void testGeneraVolcadoErpConArticulosAgrupados() {
        VolcadoErpGenerationService service = new VolcadoErpGenerationService();

        List<CajaData> cajas = new ArrayList<>();
        cajas.add(new CajaData("REF001", "001", "80", 50, null, null));
        cajas.add(new CajaData("REF001", "001", "80", 30, null, null));
        cajas.add(new CajaData("REF001", "002", "90", 20, null, null));

        DatosEnvio envio = new DatosEnvio();
        envio.setNumeroFactura("FA-26-1189");

        VolcadoErpData resultado = service.generar(cajas, envio);

        assertNotNull(resultado);
        // 2 líneas únicas: REF001-80-001 (50+30=80 uds sumadas) y REF001-90-002 (20 uds)
        assertEquals(2, resultado.getTotalLineas());
        assertEquals(80, resultado.getLineas().get(0).getQuantitat());
        assertEquals(20, resultado.getLineas().get(1).getQuantitat());
        assertEquals("Volcado_ERP_FA-26-1189.xlsx", resultado.getNombreFichero());
    }

    @Test
    public void testGeneraColorCodiSecuencial() {
        VolcadoErpGenerationService service = new VolcadoErpGenerationService();

        List<CajaData> cajas = new ArrayList<>();
        cajas.add(new CajaData("REF001", "NOIR", "80", 50, null, null));
        cajas.add(new CajaData("REF001", "BLEU", "80", 30, null, null));
        cajas.add(new CajaData("REF001", "NOIR", "90", 20, null, null));

        DatosEnvio envio = new DatosEnvio();
        envio.setNumeroFactura("TEST");

        VolcadoErpData resultado = service.generar(cajas, envio);

        // Una línea por combinación única referencia + talla + color:
        // (REF001, 80, NOIR), (REF001, 80, BLEU), (REF001, 90, NOIR) = 3 líneas
        assertEquals(3, resultado.getTotalLineas(), "Should have 3 lines, one per referencia+talla+color");

        VolcadoErpLinea linea1 = resultado.getLineas().get(0);
        VolcadoErpLinea linea2 = resultado.getLineas().get(1);
        VolcadoErpLinea linea3 = resultado.getLineas().get(2);

        // COLORCODI secuencial por orden de primera aparición: NOIR=001, BLEU=002
        // El mismo color siempre recibe el mismo código
        assertEquals("001", linea1.getColorCodi());
        assertEquals("002", linea2.getColorCodi());
        assertEquals("001", linea3.getColorCodi());

        assertEquals(50, linea1.getQuantitat());
        assertEquals(30, linea2.getQuantitat());
        assertEquals(20, linea3.getQuantitat());
    }
}

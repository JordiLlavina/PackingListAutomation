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
        assertEquals("Volcado_ICSUITE_FA-26-1189.xlsx", resultado.getNombreFichero());
    }

    @Test
    public void testAgrupaPorReferenciaTallaYColorConLineasNumeradas() {
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

        // Lin. correlativo por orden de primera aparición del grupo
        assertEquals(1, linea1.getNumeroLinea());
        assertEquals(2, linea2.getNumeroLinea());
        assertEquals(3, linea3.getNumeroLinea());

        // Color y talla acompañan a la línea (columnas extra del fichero)
        assertEquals("NOIR", linea1.getColor());
        assertEquals("BLEU", linea2.getColor());
        assertEquals("90", linea3.getTalla());

        assertEquals(50, linea1.getQuantitat());
        assertEquals(30, linea2.getQuantitat());
        assertEquals(20, linea3.getQuantitat());
    }

    @Test
    public void testUniEsLaTallaEnCinturonesYUEnElResto() {
        VolcadoErpGenerationService service = new VolcadoErpGenerationService();

        List<CajaData> cajas = new ArrayList<>();
        cajas.add(new CajaData("USL728.AL217", "NOIR", null, 40, null, null));
        cajas.add(new CajaData("UBL029.AL0216", "NOIR", "85", 12, null, null));

        DatosEnvio envio = new DatosEnvio();
        envio.setNumeroFactura("TEST");

        VolcadoErpData resultado = service.generar(cajas, envio);

        // Los cinturones (UBL) se venden por talla: esa es su unidad.
        assertEquals("U", resultado.getLineas().get(0).getUni());
        assertEquals("85", resultado.getLineas().get(1).getUni());
    }

    @Test
    public void testLaComandaSeRepiteEnTodasLasLineasYPuedeFaltar() {
        VolcadoErpGenerationService service = new VolcadoErpGenerationService();

        List<CajaData> cajas = new ArrayList<>();
        cajas.add(new CajaData("REF001", "NOIR", "80", 50, null, null));
        cajas.add(new CajaData("REF002", "BLEU", "90", 30, null, null));

        DatosEnvio conComanda = new DatosEnvio();
        conComanda.setNumeroFactura("TEST");
        conComanda.setNumeroComanda("12345");

        VolcadoErpData resultado = service.generar(cajas, conComanda);
        assertEquals("12345", resultado.getLineas().get(0).getComanda());
        assertEquals("12345", resultado.getLineas().get(1).getComanda());

        // Sin comanda el volcado se genera igual, con la columna en blanco.
        DatosEnvio sinComanda = new DatosEnvio();
        sinComanda.setNumeroFactura("TEST");

        VolcadoErpData sinComandaResultado = service.generar(cajas, sinComanda);
        assertEquals(2, sinComandaResultado.getTotalLineas());
        assertNull(sinComandaResultado.getLineas().get(0).getComanda());
    }
}

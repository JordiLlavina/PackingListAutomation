package com.puntotres.packinglist.service;

import static com.puntotres.packinglist.testutil.TestDatos.caja;
import static com.puntotres.packinglist.testutil.TestDatos.palet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.DestinoClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

class ResolutorDestinosPadreTest {

    private final ResolutorDestinosPadre resolutor = new ResolutorDestinosPadre();

    private static ClienteConfig apc() {
        DestinoClienteConfig wholesale = new DestinoClienteConfig();
        wholesale.setNombreCliente("A.P.C.");
        wholesale.setDireccion("CROSSLOG");
        wholesale.setAbreviatura("WH");
        wholesale.setDestinosHijo(List.of("AUSTRALIA", "WHOLESALE", "CHINE FRANCH"));

        DestinoClienteConfig japan = new DestinoClienteConfig();
        japan.setNombreCliente("A.P.C. JAPAN LTD.");
        japan.setDireccion("TOKYO");
        japan.setAbreviatura("JPT");

        ClienteConfig apc = new ClienteConfig();
        apc.setNombre("A.P.C.");
        apc.setPlantilla(TipoPlantilla.APC);
        Map<String, DestinoClienteConfig> destinos = new LinkedHashMap<>();
        destinos.put("WHOLESALE", wholesale);
        destinos.put("JAPAN", japan);
        apc.setDestinos(destinos);
        return apc;
    }

    /** Cliente sin catálogo de destinos, como AMI o los genéricos. */
    private static ClienteConfig ami() {
        ClienteConfig ami = new ClienteConfig();
        ami.setNombre("AMI");
        ami.setPlantilla(TipoPlantilla.AMI);
        return ami;
    }

    private static EnvioImportado.DestinoImportado destino(String nombre, List<CajaData> cajas,
                                                           List<PaletData> palets) {
        DestinoData datos = new DestinoData();
        datos.setNombreDestino(nombre);
        datos.setCajas(new ArrayList<>(cajas));
        return new EnvioImportado.DestinoImportado(datos, new ArrayList<>(palets));
    }

    @Test
    void unaHijaTomaElNombreDelPadreYDejaSuNombreEnElCanal() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        ResultadoDestinos resultado = resolutor.resolver(
                List.of(destino("Australia", List.of(linea), List.of(palet("Australia", 1, 1, 4)))),
                apc(), "28/04/2026");

        assertEquals(1, resultado.getDestinos().size());
        assertEquals("WHOLESALE", resultado.getDestinos().get(0).getDestino().getNombreDestino());
        assertEquals("AUSTRALIA", linea.getCanal());
    }

    @Test
    void elCanalQueYaVieneEnElJsonManda() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        linea.setCanal("DOUANES USA");

        resolutor.resolver(List.of(destino("Australia", List.of(linea), List.of())),
                apc(), "28/04/2026");

        assertEquals("DOUANES USA", linea.getCanal());
    }

    @Test
    void todasLasCajasDelPadreLlevanSuLivraisonCode() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);

        resolutor.resolver(List.of(destino("Chine franch", List.of(linea), List.of())),
                apc(), "28/04/2026");

        assertEquals("PUN20260428WH1", linea.getLivraisonCode());
    }

    @Test
    void dosHijasDelMismoPadreSeFusionanEnUnSoloDestino() {
        CajaData deAustralia = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        CajaData deChina = caja(9, "719", "PXBHZ-F65101", "LZZ-NOIR", 1, null, 3.5);

        ResultadoDestinos resultado = resolutor.resolver(List.of(
                destino("Australia", List.of(deAustralia), List.of(palet("Australia", 1, 1, 4))),
                destino("Chine franch", List.of(deChina), List.of(palet("Chine franch", 2, 9, 9)))),
                apc(), "28/04/2026");

        assertEquals(1, resultado.getDestinos().size());
        EnvioImportado.DestinoImportado fusionado = resultado.getDestinos().get(0);
        assertEquals("WHOLESALE", fusionado.getDestino().getNombreDestino());
        assertEquals(2, fusionado.getDestino().getCajas().size());
        assertEquals(2, fusionado.getPalets().size());
        assertTrue(resultado.getAvisos().isEmpty());
    }

    @Test
    void avisaDeLosNumerosDeCajaRepetidosEntreHijasSinRenumerar() {
        CajaData deAustralia = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        CajaData deChina = caja(1, "719", "PXBHZ-F65101", "LZZ-NOIR", 1, null, 3.5);

        ResultadoDestinos resultado = resolutor.resolver(List.of(
                destino("Australia", List.of(deAustralia), List.of()),
                destino("Chine franch", List.of(deChina), List.of())),
                apc(), "28/04/2026");

        assertEquals(1, deAustralia.getNumeroCaja());
        assertEquals(1, deChina.getNumeroCaja());
        assertEquals(1, resultado.getAvisos().size());
        assertTrue(resultado.getAvisos().get(0).contains("caja 1"));
        assertTrue(resultado.getAvisos().get(0).contains("Australia"));
        assertTrue(resultado.getAvisos().get(0).contains("Chine franch"));
    }

    @Test
    void avisaDeLosPaletsRepetidosEntreHijasSinRenumerar() {
        CajaData deAustralia = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        CajaData deChina = caja(9, "719", "PXBHZ-F65101", "LZZ-NOIR", 1, null, 3.5);

        ResultadoDestinos resultado = resolutor.resolver(List.of(
                destino("Australia", List.of(deAustralia), List.of(palet("Australia", 1, 1, 4))),
                destino("Chine franch", List.of(deChina), List.of(palet("Chine franch", 1, 9, 9)))),
                apc(), "28/04/2026");

        assertEquals(1, resultado.getAvisos().size());
        assertTrue(resultado.getAvisos().get(0).contains("palet 1"));
    }

    @Test
    void unClienteSinCatalogoDeDestinosPasaIntacto() {
        CajaData linea = caja(1, "PO", "ULL728", "NOIR", 3, null, 4.2);
        ResultadoDestinos resultado = resolutor.resolver(
                List.of(destino("CHINA", List.of(linea), List.of())), ami(), "28/04/2026");

        assertEquals("CHINA", resultado.getDestinos().get(0).getDestino().getNombreDestino());
        assertNull(linea.getLivraisonCode());
        assertTrue(resultado.getAvisos().isEmpty());
    }

    @Test
    void unDestinoFueraDelCatalogoSeDejaComoEstaParaQueAviseLaGeneracion() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        ResultadoDestinos resultado = resolutor.resolver(
                List.of(destino("MARTE", List.of(linea), List.of())), apc(), "28/04/2026");

        assertEquals("MARTE", resultado.getDestinos().get(0).getDestino().getNombreDestino());
        assertNull(linea.getLivraisonCode());
    }
}

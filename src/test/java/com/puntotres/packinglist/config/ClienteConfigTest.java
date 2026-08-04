package com.puntotres.packinglist.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ClienteConfigTest {

    private static ClienteConfig apc() {
        DestinoClienteConfig wholesale = new DestinoClienteConfig();
        wholesale.setNombreCliente("A.P.C.");
        wholesale.setDireccion("CROSSLOG, 104 RUE DENIS PAPIN, 77550 MOISSY CRAMAYEL, FRANCE");
        wholesale.setAbreviatura("WH");
        wholesale.setDestinosHijo(List.of("AUSTRALIA", "WHOLESALE", "CHINE FRANCH"));

        DestinoClienteConfig ivry = new DestinoClienteConfig();
        ivry.setNombreCliente("A.P.C.");
        ivry.setDireccion("74 BIS AV MAURICE THOREZ 94200 IVRY SUR SEINE FRANCE");
        ivry.setAbreviatura("IVRY");

        ClienteConfig apc = new ClienteConfig();
        apc.setPlantilla(TipoPlantilla.APC);
        apc.setDestinos(Map.of("WHOLESALE", wholesale, "IVRY", ivry));
        return apc;
    }

    @Test
    void unaHijaResuelveASuDestinoPadre() {
        ClienteConfig.DestinoResuelto resuelto =
                apc().destinoPadrePara("Chine franch").orElseThrow();

        assertEquals("WHOLESALE", resuelto.nombrePadre());
        assertEquals("WH", resuelto.config().getAbreviatura());
    }

    @Test
    void elPadreSeResuelveASiMismoAunqueSeaTambienHijaDeSuPropiaLista() {
        ClienteConfig.DestinoResuelto resuelto =
                apc().destinoPadrePara("wholesale ").orElseThrow();

        assertEquals("WHOLESALE", resuelto.nombrePadre());
    }

    @Test
    void unDestinoSinHijasSeResuelveASiMismo() {
        assertEquals("IVRY", apc().destinoPadrePara("IVRY").orElseThrow().nombrePadre());
    }

    @Test
    void destinoDesconocidoONuloDevuelveVacio() {
        assertTrue(apc().destinoPadrePara("MARTE").isEmpty());
        assertTrue(apc().destinoPadrePara(null).isEmpty());
    }

    @Test
    void unDestinoSinHijasConfiguradasDevuelveListaVaciaNoNull() {
        assertTrue(new DestinoClienteConfig().getDestinosHijo().isEmpty());
    }
}

package com.puntotres.packinglist.shipping.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class ShipmentTest {

    @Test
    void elValorDeclaradoEsLaSumaDeCantidadPorValorUnitario() {
        Shipment envio = new Shipment("Jordi");
        envio.getLineasAduana().add(new CustomsLineItem("Bolsos", "42022100", 10, new BigDecimal("12.50"), 0.4, 0.5, "MA", null));
        envio.getLineasAduana().add(new CustomsLineItem("Cinturones", "42033000", 4, new BigDecimal("3.00"), 0.1, 0.12, "MA", null));

        assertEquals(new BigDecimal("137.00"), envio.valorDeclarado());
    }

    @Test
    void elPesoDeLosBultosYElBrutoDeLasLineasSeSumanSaltandoLosNulos() {
        Shipment envio = new Shipment("Jordi");
        envio.getBultos().add(new ShipmentPackage(5.0, 40.0, 30.0, 20.0, null));
        envio.getBultos().add(new ShipmentPackage(null, 40.0, 30.0, 20.0, null));
        envio.getLineasAduana().add(new CustomsLineItem("Bolsos", "42022100", 10, BigDecimal.ONE, 0.4, 4.5, "MA", null));

        assertEquals(5.0, envio.pesoBultos(), 0.001);
        assertEquals(4.5, envio.pesoBrutoLineas(), 0.001);
    }

    @Test
    void cambiarDeEstadoDejaUnaFilaDeHistorialConElAnteriorYElNuevo() {
        Shipment envio = new Shipment("Jordi");

        envio.cambiarEstado(ShipmentStatus.QUOTED, "Jordi", "3 productos");

        assertEquals(ShipmentStatus.QUOTED, envio.getEstado());
        assertEquals(1, envio.getHistorial().size());
        assertEquals(ShipmentStatus.DRAFT, envio.getHistorial().get(0).getEstadoAnterior());
        assertEquals("3 productos", envio.getHistorial().get(0).getDetalle());
    }

    @Test
    void reemplazarOfertasBorraLasAnterioresYBorrarOfertasDejaLaListaVacia() {
        Shipment envio = new Shipment("Jordi");
        envio.reemplazarOfertas(List.of(new RateOffer("P", "EXPRESS WORLDWIDE", new BigDecimal("50"), "EUR", null)));
        envio.reemplazarOfertas(List.of(new RateOffer("N", "DOMESTIC", new BigDecimal("10"), "EUR", null)));
        assertEquals(List.of("N"), envio.getOfertas().stream().map(RateOffer::getProductoCodigo).toList());

        envio.borrarOfertas();
        assertTrue(envio.getOfertas().isEmpty());
    }
}

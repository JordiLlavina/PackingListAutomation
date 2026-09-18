package com.puntotres.packinglist.shipping.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Que V2 y las entidades cuadren lo comprueba ddl-auto: validate al arrancar;
 * aquí se comprueba además que se puede guardar y releer un envío completo
 * con sus colecciones, que es donde un @OrderColumn o una FK mal puesta se
 * manifiesta.
 */
@SpringBootTest
@Transactional
class EsquemaEnviosTest {

    @Autowired private ShipmentRepository envios;
    @Autowired private ContactRepository contactos;
    @Autowired private BillingAccountRepository cuentas;
    @Autowired private ShipmentReasonRepository motivos;

    @Test
    void laSemillaDeMotivosEstaCargada() {
        assertEquals(5, motivos.findByActivoTrueOrderByNombreAsc().size());
        assertEquals(ExportReasonType.SAMPLE, motivos.findByNombreIgnoreCase("muestras").orElseThrow().getMotivoDhl());
    }

    @Test
    void unEnvioCompletoSeGuardaYSeRelee() {
        Contact origen = contactos.save(new Contact("PUNTOTRES", "C/Industria 585", "Badalona", "ES"));
        Contact destino = contactos.save(new Contact("TALLER", "Zone franche", "Tanger", "MA"));
        BillingAccount cuenta = cuentas.save(new BillingAccount("300112049", "Puntotres", true, EnumSet.allOf(BillingRole.class)));

        Shipment envio = new Shipment("Jordi");
        envio.setRemitente(origen);
        envio.setDestinatario(destino);
        envio.setCuentaTransporte(cuenta);
        envio.setMotivo(motivos.findByNombreIgnoreCase("Muestras").orElseThrow());
        envio.getBultos().add(new ShipmentPackage(2.5, 40.0, 30.0, 20.0, null));
        envio.getLineasAduana().add(new CustomsLineItem("Bolsos de piel", "42022100", 3, new BigDecimal("40.00"), 1.2, 1.5, "MA", null));
        envio.reemplazarOfertas(java.util.List.of(new RateOffer("P", "EXPRESS WORLDWIDE", new BigDecimal("61.20"), "EUR", null)));
        envio.anadirDocumento("label", "PDF", new byte[] {1, 2, 3});
        envio.cambiarEstado(ShipmentStatus.QUOTED, "Jordi", null);
        Long id = envios.saveAndFlush(envio).getId();

        Shipment releido = envios.findById(id).orElseThrow();
        assertEquals(1, releido.getBultos().size());
        assertEquals(1, releido.getLineasAduana().size());
        assertEquals("P", releido.getOfertas().get(0).getProductoCodigo());
        assertEquals(1, releido.getDocumentos().size());
        assertEquals(ShipmentStatus.QUOTED, releido.getHistorial().get(0).getEstadoNuevo());
        assertEquals(new BigDecimal("120.00"), releido.valorDeclarado());
    }
}

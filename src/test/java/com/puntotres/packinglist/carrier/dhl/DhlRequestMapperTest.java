package com.puntotres.packinglist.carrier.dhl;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.puntotres.packinglist.carrier.dhl.dto.*;
import com.puntotres.packinglist.shipping.model.*;

class DhlRequestMapperTest {

    private DhlRequestMapper mapper;
    private Shipment envio;

    @BeforeEach
    void preparar() {
        DhlProperties props = new DhlProperties();
        props.setShipperAccount("300112049");
        props.setHoraRecogida(LocalTime.of(12, 0));
        props.setZonasHorarias(Map.of("ES", "Europe/Madrid", "MA", "Africa/Casablanca"));
        props.setPlantillaEtiqueta("ECOM26_84_A4_001");
        mapper = new DhlRequestMapper(props);

        Contact origen = new Contact("PUNTOTRES BAGS & BELTS SLU", "C/Industria 585-587", "Badalona", "ES");
        origen.setPersona("Carme Fabrega"); origen.setCodigoPostal("08918"); origen.setTelefono("+34934425135");
        origen.setEmail("export@puntotres.com"); origen.setNifVat("ESB67565291"); origen.setEori("ESB67565291");
        Contact destino = new Contact("MILITZER & MUNCH", "Zone logistique Tanger", "Tangiers", "MA");
        destino.setDireccion2("Free zone gate 2"); destino.setCodigoPostal("90000"); destino.setTelefono("+212539393394");
        destino.setTipo(ContactType.BUSINESS);

        BillingAccount propia = new BillingAccount("300112049", "Puntotres", true, EnumSet.allOf(BillingRole.class));
        BillingAccount ami = new BillingAccount("956483537", "AMI PARIS", false, EnumSet.of(BillingRole.PAYER, BillingRole.DUTIES));

        envio = new Shipment("Jordi");
        envio.setRemitente(origen); envio.setDestinatario(destino);
        envio.setFechaRecogida(LocalDate.of(2026, 9, 21));
        envio.setIncoterm(Incoterm.DAP);
        envio.setCuentaTransporte(ami); envio.setCuentaAranceles(ami);
        envio.setMotivo(new ShipmentReason("Muestras", ExportReasonType.SAMPLE));
        envio.setReferencia("MUESTRAS-H26-01");
        envio.setPedirRecogida(true); envio.setHoraCierreRecogida("18:00"); envio.setLugarRecogida("Recepción");
        envio.setAduanero(true);
        envio.setProductoCodigo("P");
        envio.setRequestId("d0e7832e-5c98-11ea-bc55-0242ac130003");
        envio.getBultos().add(new ShipmentPackage(2.5, 40.0, 30.0, 20.0, null));
        envio.getLineasAduana().add(new CustomsLineItem("Bolsos de piel", "42022100", 3, new BigDecimal("40.00"), 1.0, 1.5, "MA", null));
        envio.getLineasAduana().add(new CustomsLineItem("Cinturones", "42033000", 10, new BigDecimal("8.50"), 0.5, 0.6, "MA", null));
    }

    @Test
    void laFechaPlanificadaLlevaLaHoraDeRecogidaEnLaZonaDelOrigen() {
        // 21 de septiembre en Madrid es horario de verano: +02:00. Formato del OpenAPI: yyyy-MM-ddTHH:mm:ss GMT+hh:mm
        assertEquals("2026-09-21T12:00:00 GMT+02:00", mapper.fechaPlanificada(envio));
    }

    @Test
    void laTarifaLlevaDireccionesCuentaPropiaBultosYValorDeclarado() {
        DhlRateRequest peticion = mapper.tarifa(envio);

        assertEquals("08918", peticion.customerDetails().shipperDetails().postalCode());
        assertEquals("MA", peticion.customerDetails().receiverDetails().countryCode());
        assertEquals(List.of(new DhlAccount("shipper", "300112049")), peticion.accounts());
        assertEquals("metric", peticion.unitOfMeasurement());
        assertTrue(peticion.isCustomsDeclarable());
        assertEquals(new BigDecimal("205.00"), peticion.monetaryAmount().get(0).value());
        assertEquals("EUR", peticion.monetaryAmount().get(0).currency());
        assertEquals("all", peticion.productTypeCode());
        assertEquals(2.5, peticion.packages().get(0).weight());
        assertEquals(20.0, peticion.packages().get(0).dimensions().height());
    }

    @Test
    void elEnvioLlevaLasTresCuentasCuandoPagaUnTercero() {
        DhlShipmentRequest peticion = mapper.envio(envio);

        assertEquals(List.of(new DhlAccount("shipper", "300112049"), new DhlAccount("payer", "956483537"),
                new DhlAccount("duties-taxes", "956483537")), peticion.accounts());
        assertEquals(new DhlPickup(true, "18:00", "Recepción"), peticion.pickup());
        assertEquals("P", peticion.productCode());
    }

    @Test
    void sinPedirRecogidaElPickupVaAFalseYSinHoraNiLugar() {
        envio.setPedirRecogida(false);

        assertEquals(new DhlPickup(false, null, null), mapper.envio(envio).pickup());
    }

    @Test
    void siLaCuentaPropiaPagaElTransporteNoSeRepiteComoPayer() {
        envio.setCuentaTransporte(new BillingAccount("300112049", "Puntotres", true, EnumSet.allOf(BillingRole.class)));
        envio.setCuentaAranceles(null);

        assertEquals(List.of(new DhlAccount("shipper", "300112049")), mapper.envio(envio).accounts());
    }

    @Test
    void losContactosLlevanDireccionContactoYRegistrosFiscales() {
        DhlParty remitente = mapper.envio(envio).customerDetails().shipperDetails();

        assertEquals("Badalona", remitente.postalAddress().cityName());
        assertEquals("C/Industria 585-587", remitente.postalAddress().addressLine1());
        assertEquals("PUNTOTRES BAGS & BELTS SLU", remitente.contactInformation().companyName());
        assertEquals("Carme Fabrega", remitente.contactInformation().fullName());
        assertEquals("+34934425135", remitente.contactInformation().phone());
        assertEquals("business", remitente.typeCode());
        assertEquals(List.of(new DhlRegistrationNumber("VAT", "ESB67565291", "ES"), new DhlRegistrationNumber("EOR", "ESB67565291", "ES")),
                remitente.registrationNumbers());
    }

    @Test
    void sinPersonaElFullNameEsLaEmpresaYSinRegistrosLaListaEsNula() {
        DhlParty destinatario = mapper.envio(envio).customerDetails().receiverDetails();

        assertEquals("MILITZER & MUNCH", destinatario.contactInformation().fullName());
        assertNull(destinatario.registrationNumbers(), "una lista vacía la rechaza el esquema; nula no se serializa");
    }

    @Test
    void elContenidoLlevaLaDeclaracionDeExportacionConLineasNumeradas() {
        DhlContent contenido = mapper.envio(envio).content();

        assertTrue(contenido.isCustomsDeclarable());
        assertEquals(new BigDecimal("205.00"), contenido.declaredValue());
        assertEquals("EUR", contenido.declaredValueCurrency());
        assertEquals("DAP", contenido.incoterm());
        assertEquals("Muestras", contenido.description());
        assertEquals("MUESTRAS-H26-01", contenido.packages().get(0).customerReferences().get(0).value());

        DhlExportDeclaration declaracion = contenido.exportDeclaration();
        assertEquals("sample", declaracion.exportReasonType());
        assertEquals("Tangiers", declaracion.placeOfIncoterm());
        assertEquals("2026-09-21", declaracion.invoice().date());
        DhlLineItem primera = declaracion.lineItems().get(0);
        assertEquals(1, primera.number());
        assertEquals(new DhlQuantity(3, "PCS"), primera.quantity());
        assertEquals(List.of(new DhlCommodityCode("outbound", "42022100")), primera.commodityCodes());
        assertEquals("MA", primera.manufacturerCountry());
        assertEquals(new DhlLineWeight(1.0, 1.5), primera.weight());
        assertEquals(2, declaracion.lineItems().get(1).number());
    }

    @Test
    void elNumeroDeFacturaEsPTAnioYIdDelEnvio() {
        ReflectionTestUtils.setField(envio, "id", 42L);

        DhlExportDeclaration declaracion = mapper.envio(envio).content().exportDeclaration();

        assertEquals("PT-2026-42", declaracion.invoice().number());
    }

    @Test
    void sinAduanaNoHayDeclaracionNiFacturaEnLosDocumentos() {
        envio.setAduanero(false);

        DhlShipmentRequest peticion = mapper.envio(envio);

        assertNull(peticion.content().exportDeclaration());
        assertFalse(peticion.content().isCustomsDeclarable());
        assertTrue(peticion.outputImageProperties().imageOptions().stream().noneMatch(o -> o.typeCode().equals("invoice")));
    }

    @Test
    void losDocumentosPedidosSonEtiquetaA4WaybillYFacturaComercialEnPdf() {
        DhlOutputImageProperties salida = mapper.envio(envio).outputImageProperties();

        assertEquals("pdf", salida.encodingFormat());
        assertEquals(300, salida.printerDPI());
        List<String> tipos = salida.imageOptions().stream().map(DhlImageOption::typeCode).toList();
        assertEquals(List.of("label", "waybillDoc", "invoice"), tipos);
        assertEquals("ECOM26_84_A4_001", salida.imageOptions().get(0).templateName());
        assertEquals("COMMERCIAL_INVOICE_P_10", salida.imageOptions().get(2).templateName());
        assertTrue(salida.splitTransportAndWaybillDocLabels());
    }
}

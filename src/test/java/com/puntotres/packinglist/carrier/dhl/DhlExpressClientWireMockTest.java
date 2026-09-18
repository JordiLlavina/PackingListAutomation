package com.puntotres.packinglist.carrier.dhl;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.puntotres.packinglist.shipping.carrier.*;
import com.puntotres.packinglist.shipping.model.*;

class DhlExpressClientWireMockTest {

    private static WireMockServer dhl;
    private DhlExpressClient cliente;
    private Shipment envio;

    @BeforeAll
    static void arrancar() {
        dhl = new WireMockServer(WireMockConfiguration.options().dynamicPort().usingFilesUnderClasspath("ejemplos/envios"));
        dhl.start();
    }

    @AfterAll
    static void parar() { dhl.stop(); }

    @BeforeEach
    void preparar() {
        dhl.resetAll();
        DhlProperties props = new DhlProperties();
        props.setBaseUrl(dhl.baseUrl());
        props.setApiKey("clave-api");
        props.setApiSecret("secreto");
        props.setShipperAccount("300112049");
        props.setZonasHorarias(Map.of("ES", "Europe/Madrid"));
        cliente = new DhlExpressClient(props, RestClient.builder(), new ObjectMapper());

        Contact origen = new Contact("PUNTOTRES", "C/Industria 585", "Badalona", "ES");
        origen.setCodigoPostal("08918"); origen.setTelefono("+34934425135");
        Contact destino = new Contact("TALLER", "Zone franche", "Tanger", "MA");
        destino.setCodigoPostal("90000"); destino.setTelefono("+212539393394");
        envio = new Shipment("Jordi");
        envio.setRemitente(origen); envio.setDestinatario(destino);
        envio.setFechaRecogida(LocalDate.now().plusDays(1));
        envio.setMotivo(new ShipmentReason("Muestras", ExportReasonType.SAMPLE));
        envio.setCuentaTransporte(new BillingAccount("300112049", "Puntotres", true, EnumSet.allOf(BillingRole.class)));
        envio.setProductoCodigo("P");
        envio.setHoraCierreRecogida("18:00");
        envio.setRequestId("d0e7832e-5c98-11ea-bc55-0242ac130003");
        envio.getBultos().add(new ShipmentPackage(2.5, 40.0, 30.0, 20.0, null));
        envio.getLineasAduana().add(new CustomsLineItem("Bolsos", "42022100", 3, new BigDecimal("40"), 1.0, 1.5, "MA", null));
    }

    @Test
    void tarifarLlamaAPostRatesConBasicAuthYCabecerasYDevuelveLasOfertas() {
        dhl.stubFor(post(urlEqualTo("/rates")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("dhl-rates-response.json")));

        List<ProductOffer> ofertas = cliente.quote(envio);

        assertEquals(2, ofertas.size());
        assertEquals(new BigDecimal("61.2"), ofertas.get(0).precio());
        dhl.verify(postRequestedFor(urlEqualTo("/rates"))
                .withBasicAuth(new com.github.tomakehurst.wiremock.client.BasicCredentials("clave-api", "secreto"))
                .withHeader("Message-Reference", matching("[0-9a-f-]{36}"))
                .withHeader("Message-Reference-Date", matching(".+GMT"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(matchingJsonPath("$.accounts[0].number", equalTo("300112049")))
                .withRequestBody(matchingJsonPath("$.packages[0].weight", equalTo("2.5")))
                .withRequestBody(notMatching(".*null.*")));
    }

    @Test
    void crearMandaElRequestIdComoMessageReferenceYDevuelveGuiaYDocumentos() {
        dhl.stubFor(post(urlEqualTo("/shipments")).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBodyFile("dhl-shipment-response.json")));

        CreatedShipment creado = cliente.createShipment(envio);

        assertEquals("1103733901", creado.numeroGuia());
        assertEquals(3, creado.documentos().size());
        dhl.verify(postRequestedFor(urlEqualTo("/shipments"))
                .withHeader("Message-Reference", equalTo("d0e7832e-5c98-11ea-bc55-0242ac130003"))
                .withRequestBody(matchingJsonPath("$.productCode", equalTo("P")))
                .withRequestBody(matchingJsonPath("$.pickup.isRequested", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.pickup.closeTime", equalTo("18:00")))
                .withRequestBody(matchingJsonPath("$.content.exportDeclaration.lineItems[0].commodityCodes[0].value", equalTo("42022100"))));
    }

    @Test
    void unErrorDeValidacionSeTraduceACarrierExceptionLegible() {
        dhl.stubFor(post(urlEqualTo("/shipments")).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/problem+json").withBodyFile("dhl-error-400.json")));

        CarrierException e = assertThrows(CarrierException.class, () -> cliente.createShipment(envio));

        assertTrue(e.getMensajeUsuario().startsWith("DHL (400) Validation error:"));
        assertTrue(e.getMensajeUsuario().contains("postalCode: is missing"));
    }

    @Test
    void sinRespuestaDelServidorLaExcepcionDiceQueDhlNoHaRespondido() {
        dhl.stubFor(post(urlEqualTo("/rates")).willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        CarrierException e = assertThrows(CarrierException.class, () -> cliente.quote(envio));

        assertTrue(e.getMensajeUsuario().startsWith("DHL no ha respondido"), e.getMensajeUsuario());
    }

    @Test
    void validarDireccionLlamaAGetAddressValidateConLosParametros() {
        dhl.stubFor(get(urlPathEqualTo("/address-validate")).willReturn(okJson(
                "{\"warnings\":[],\"address\":[{\"countryCode\":\"MA\",\"postalCode\":\"90000\",\"cityName\":\"TANGER\",\"serviceArea\":{\"code\":\"TNG\",\"description\":\"TANGIER-MOROCCO\"}}]}")));

        AddressCheck resultado = cliente.validateAddress(envio.getDestinatario(), TipoDireccion.ENTREGA);

        assertTrue(resultado.valida());
        assertTrue(resultado.detalle().contains("TANGIER-MOROCCO"));
        dhl.verify(getRequestedFor(urlPathEqualTo("/address-validate"))
                .withQueryParam("type", equalTo("delivery"))
                .withQueryParam("countryCode", equalTo("MA"))
                .withQueryParam("postalCode", equalTo("90000"))
                .withQueryParam("cityName", equalTo("Tanger"))
                .withQueryParam("strictValidation", equalTo("false")));
    }

    @Test
    void sinCredencialesNoLlamaYExplicaQueVariablesFaltan() {
        DhlProperties vacias = new DhlProperties();
        vacias.setBaseUrl(dhl.baseUrl());
        DhlExpressClient sinConfigurar = new DhlExpressClient(vacias, RestClient.builder(), new ObjectMapper());

        CarrierException e = assertThrows(CarrierException.class, () -> sinConfigurar.quote(envio));

        assertTrue(e.getMensajeUsuario().contains("DHL_API_KEY"));
        dhl.verify(0, postRequestedFor(urlEqualTo("/rates")));
    }

    @Test
    void trackEsDeLaFase2() {
        assertThrows(UnsupportedOperationException.class, () -> cliente.track("1103733901"));
    }
}

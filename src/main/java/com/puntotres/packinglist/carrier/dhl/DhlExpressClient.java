package com.puntotres.packinglist.carrier.dhl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.carrier.dhl.dto.*;
import com.puntotres.packinglist.shipping.carrier.*;
import com.puntotres.packinglist.shipping.model.CarrierCode;
import com.puntotres.packinglist.shipping.model.Contact;
import com.puntotres.packinglist.shipping.model.Shipment;

/**
 * MyDHL API (OpenAPI 3.3.2): POST /rates, POST /shipments, GET /address-validate.
 * El RestClient se construye perezosamente y solo si hay credenciales: sin
 * variables de entorno la aplicación arranca igual y la sección avisa.
 */
@Component
public class DhlExpressClient implements CarrierClient {

    private static final DateTimeFormatter FECHA_CABECERA = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final DhlProperties props;
    private final RestClient.Builder builder;
    private final ObjectMapper json;
    private final DhlRequestMapper peticiones;
    private final DhlResponseMapper respuestas = new DhlResponseMapper();
    private final DhlErrorTranslator errores;
    private volatile RestClient rest;

    public DhlExpressClient(DhlProperties props, RestClient.Builder builder, ObjectMapper json) {
        this.props = props;
        this.builder = builder;
        this.json = json;
        this.peticiones = new DhlRequestMapper(props);
        this.errores = new DhlErrorTranslator(json);
    }

    @Override public CarrierCode codigo() { return CarrierCode.DHL_EXPRESS; }

    @Override
    public List<ProductOffer> getProducts(Shipment envio) {
        return quote(envio).stream().map(o -> new ProductOffer(o.codigo(), o.nombre(), null, null, o.entregaEstimada())).toList();
    }

    @Override
    public List<ProductOffer> quote(Shipment envio) {
        DhlRatesResponse respuesta = ejecutar(() -> rest().post().uri("/rates")
                .headers(h -> referencia(h, UUID.randomUUID().toString()))
                .body(peticiones.tarifa(envio))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::traducirError)
                .body(DhlRatesResponse.class));
        return respuestas.ofertas(respuesta);
    }

    @Override
    public CreatedShipment createShipment(Shipment envio) {
        DhlShipmentResponse respuesta = ejecutar(() -> rest().post().uri("/shipments")
                .headers(h -> referencia(h, envio.getRequestId()))
                .body(peticiones.envio(envio))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::traducirError)
                .body(DhlShipmentResponse.class));
        return respuestas.creado(respuesta);
    }

    @Override
    public AddressCheck validateAddress(Contact contacto, TipoDireccion tipo) {
        DhlAddressValidateResponse respuesta = ejecutar(() -> rest().get()
                .uri(b -> b.path("/address-validate")
                        .queryParam("type", tipo == TipoDireccion.RECOGIDA ? "pickup" : "delivery")
                        .queryParam("countryCode", contacto.getPais())
                        .queryParamIfPresent("postalCode", java.util.Optional.ofNullable(contacto.getCodigoPostal()))
                        .queryParamIfPresent("cityName", java.util.Optional.ofNullable(contacto.getCiudad()))
                        .queryParam("strictValidation", "false")
                        .build())
                .headers(h -> referencia(h, UUID.randomUUID().toString()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::traducirError)
                .body(DhlAddressValidateResponse.class));
        boolean valida = respuesta != null && respuesta.address() != null && !respuesta.address().isEmpty();
        StringBuilder detalle = new StringBuilder();
        if (valida) {
            DhlValidatedAddress a = respuesta.address().get(0);
            detalle.append("DHL sirve ").append(a.cityName()).append(" ").append(a.postalCode() == null ? "" : a.postalCode());
            if (a.serviceArea() != null) detalle.append(" (").append(a.serviceArea().description()).append(")");
        } else {
            detalle.append("DHL no reconoce la dirección");
        }
        if (respuesta != null && respuesta.warnings() != null) respuesta.warnings().forEach(w -> detalle.append(". ").append(w));
        return new AddressCheck(valida, detalle.toString());
    }

    @Override
    public List<TrackingEvent> track(String numeroGuia) {
        throw new UnsupportedOperationException("El seguimiento es de la fase 2");
    }

    private RestClient rest() {
        if (!props.configurado()) {
            throw new CarrierException("Faltan las credenciales de DHL: variables de entorno DHL_API_KEY, DHL_API_SECRET y DHL_SHIPPER_ACCOUNT.");
        }
        RestClient actual = rest;
        if (actual == null) {
            synchronized (this) {
                if (rest == null) rest = DhlRestClientFactory.crear(props, builder, json);
                actual = rest;
            }
        }
        return actual;
    }

    private static void referencia(org.springframework.http.HttpHeaders h, String requestId) {
        h.set("Message-Reference", requestId);
        h.set("Message-Reference-Date", FECHA_CABECERA.format(ZonedDateTime.now(ZoneOffset.UTC)));
    }

    private void traducirError(org.springframework.http.HttpRequest peticion, ClientHttpResponse respuesta) throws IOException {
        String cuerpo = StreamUtils.copyToString(respuesta.getBody(), StandardCharsets.UTF_8);
        throw new CarrierException(errores.traducir(respuesta.getStatusCode().value(), cuerpo));
    }

    private static <T> T ejecutar(java.util.function.Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (CarrierException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new CarrierException("DHL no ha respondido (" + e.getMostSpecificCause().getClass().getSimpleName() + "). Reintentar en unos minutos.", e);
        } catch (RuntimeException e) {
            throw new CarrierException("Error hablando con DHL: " + e.getMessage(), e);
        }
    }
}

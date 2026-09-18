package com.puntotres.packinglist.carrier.dhl;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.puntotres.packinglist.carrier.dhl.dto.*;
import com.puntotres.packinglist.shipping.carrier.CarrierDocument;
import com.puntotres.packinglist.shipping.carrier.CarrierException;
import com.puntotres.packinglist.shipping.carrier.CreatedShipment;
import com.puntotres.packinglist.shipping.carrier.ProductOffer;

/** De las respuestas de MyDHL API a los tipos del núcleo. */
public class DhlResponseMapper {

    public List<ProductOffer> ofertas(DhlRatesResponse respuesta) {
        List<ProductOffer> ofertas = new ArrayList<>();
        if (respuesta == null || respuesta.products() == null) return ofertas;
        for (DhlProduct p : respuesta.products()) {
            // BILLC es la moneda en la que DHL factura la cuenta; es el precio que se pagará.
            DhlTotalPrice facturado = p.totalPrice() == null ? null : p.totalPrice().stream()
                    .filter(t -> "BILLC".equals(t.currencyType())).findFirst()
                    .orElse(p.totalPrice().isEmpty() ? null : p.totalPrice().get(0));
            LocalDateTime entrega = p.deliveryCapabilities() == null ? null : fecha(p.deliveryCapabilities().estimatedDeliveryDateAndTime());
            ofertas.add(new ProductOffer(p.productCode(), p.productName(),
                    facturado == null ? null : facturado.price(), facturado == null ? null : facturado.priceCurrency(), entrega));
        }
        return ofertas;
    }

    public CreatedShipment creado(DhlShipmentResponse respuesta) {
        // Un 2xx con un cuerpo que no encaja el contrato (sin cuerpo, un documento sin
        // content, un base64 roto) no es un NullPointerException para quien llama: es
        // DHL respondiendo mal, y ShipmentService.crear solo sabe reaccionar a
        // CarrierException, no a cualquier RuntimeException del mapeo.
        if (respuesta == null) throw new CarrierException("DHL ha respondido sin cuerpo al crear el envío.");
        List<CarrierDocument> documentos = new ArrayList<>();
        if (respuesta.documents() != null) {
            for (DhlResponseDocument d : respuesta.documents()) {
                if (d.content() == null) {
                    throw new CarrierException("DHL no ha incluido el contenido del documento " + d.typeCode() + ".");
                }
                byte[] contenido;
                try {
                    contenido = Base64.getDecoder().decode(d.content());
                } catch (IllegalArgumentException e) {
                    throw new CarrierException("El documento " + d.typeCode() + " que ha devuelto DHL no es base64 válido.");
                }
                documentos.add(new CarrierDocument(d.typeCode(), d.imageFormat(), contenido));
            }
        }
        return new CreatedShipment(respuesta.shipmentTrackingNumber(), respuesta.trackingUrl(), respuesta.dispatchConfirmationNumber(), documentos);
    }

    private static LocalDateTime fecha(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDateTime.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

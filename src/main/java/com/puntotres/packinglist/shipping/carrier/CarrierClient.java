package com.puntotres.packinglist.shipping.carrier;

import java.util.List;

import com.puntotres.packinglist.shipping.model.CarrierCode;
import com.puntotres.packinglist.shipping.model.Contact;
import com.puntotres.packinglist.shipping.model.Shipment;

/**
 * Lo que el núcleo necesita de un transportista. Habla solo tipos del núcleo:
 * los DTOs de cada API se quedan en su paquete carrier.<nombre>.
 * Todas las operaciones lanzan CarrierException si el transportista falla.
 */
public interface CarrierClient {

    CarrierCode codigo();

    /** Productos disponibles para el envío, sin precio. */
    List<ProductOffer> getProducts(Shipment envio);

    /** Productos con precio. */
    List<ProductOffer> quote(Shipment envio);

    /** Crea el envío y devuelve guía y documentos. El envío ya trae productoCodigo y requestId. */
    CreatedShipment createShipment(Shipment envio);

    AddressCheck validateAddress(Contact contacto, TipoDireccion tipo);

    /** Fase 2. Los adaptadores de la fase 1 lanzan UnsupportedOperationException. */
    List<TrackingEvent> track(String numeroGuia);
}

package com.puntotres.packinglist.shipping.carrier;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.shipping.model.CarrierCode;
import com.puntotres.packinglist.shipping.model.Contact;
import com.puntotres.packinglist.shipping.model.Shipment;

class CarrierClientRegistryTest {

    private static CarrierClient cliente(CarrierCode codigo) {
        return new CarrierClient() {
            public CarrierCode codigo() { return codigo; }
            public List<ProductOffer> getProducts(Shipment envio) { return List.of(); }
            public List<ProductOffer> quote(Shipment envio) { return List.of(); }
            public CreatedShipment createShipment(Shipment envio) { return null; }
            public AddressCheck validateAddress(Contact contacto, TipoDireccion tipo) { return null; }
            public List<TrackingEvent> track(String numeroGuia) { return List.of(); }
        };
    }

    @Test
    void devuelveElClienteDeCadaTransportista() {
        CarrierClient dhl = cliente(CarrierCode.DHL_EXPRESS);
        CarrierClientRegistry registro = new CarrierClientRegistry(List.of(dhl));

        assertSame(dhl, registro.para(CarrierCode.DHL_EXPRESS));
    }

    @Test
    void sinAdaptadorParaUnTransportistaLanzaConMensajeLegible() {
        CarrierClientRegistry registro = new CarrierClientRegistry(List.of());

        CarrierException e = assertThrows(CarrierException.class, () -> registro.para(CarrierCode.DHL_EXPRESS));
        assertTrue(e.getMensajeUsuario().contains("DHL_EXPRESS"));
    }
}

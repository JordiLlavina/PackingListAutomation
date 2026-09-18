package com.puntotres.packinglist.carrier.dhl;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.carrier.dhl.dto.DhlRatesResponse;
import com.puntotres.packinglist.carrier.dhl.dto.DhlShipmentResponse;
import com.puntotres.packinglist.shipping.carrier.CarrierException;
import com.puntotres.packinglist.shipping.carrier.CreatedShipment;
import com.puntotres.packinglist.shipping.carrier.ProductOffer;

class DhlResponseMapperTest {

    private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final DhlResponseMapper mapper = new DhlResponseMapper();

    private <T> T leer(String fichero, Class<T> tipo) throws Exception {
        return json.readValue(new ClassPathResource("ejemplos/envios/__files/" + fichero).getInputStream(), tipo);
    }

    @Test
    void lasOfertasLlevanElPrecioFacturado_BILLC_YLaEntregaEstimada() throws Exception {
        List<ProductOffer> ofertas = mapper.ofertas(leer("dhl-rates-response.json", DhlRatesResponse.class));

        assertEquals(2, ofertas.size());
        assertEquals(new ProductOffer("P", "EXPRESS WORLDWIDE NONDOC", new BigDecimal("61.2"), "EUR", LocalDateTime.of(2026, 9, 23, 23, 59)), ofertas.get(0));
        assertEquals("Y", ofertas.get(1).codigo());
    }

    @Test
    void unProductoSinPrecioOSinFechaNoRompeElMapeo() throws Exception {
        DhlRatesResponse respuesta = json.readValue("{\"products\":[{\"productName\":\"X\",\"productCode\":\"X\"}]}", DhlRatesResponse.class);

        ProductOffer oferta = mapper.ofertas(respuesta).get(0);

        assertNull(oferta.precio());
        assertNull(oferta.entregaEstimada());
    }

    @Test
    void elEnvioCreadoDecodificaLosDocumentosBase64() throws Exception {
        CreatedShipment creado = mapper.creado(leer("dhl-shipment-response.json", DhlShipmentResponse.class));

        assertEquals("1103733901", creado.numeroGuia());
        assertEquals("PRG240918123", creado.numeroConfirmacionRecogida());
        assertEquals(3, creado.documentos().size());
        assertEquals("label", creado.documentos().get(0).tipo());
        assertEquals("PDF", creado.documentos().get(0).formato());
        assertTrue(new String(creado.documentos().get(0).contenido(), StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    }

    @Test
    void unaRespuestaSinCuerpoLanzaCarrierException() {
        assertThrows(CarrierException.class, () -> mapper.creado(null));
    }

    @Test
    void unDocumentoSinContenidoLanzaCarrierExceptionConElTypeCode() throws Exception {
        DhlShipmentResponse respuesta = json.readValue(
                "{\"shipmentTrackingNumber\":\"1\",\"documents\":[{\"typeCode\":\"label\",\"imageFormat\":\"PDF\",\"content\":null}]}",
                DhlShipmentResponse.class);

        CarrierException e = assertThrows(CarrierException.class, () -> mapper.creado(respuesta));

        assertTrue(e.getMensajeUsuario().contains("label"), e.getMensajeUsuario());
    }
}

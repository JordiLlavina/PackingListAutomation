package com.puntotres.packinglist.carrier.dhl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;

class DhlErrorTranslatorTest {

    private final DhlErrorTranslator traductor = new DhlErrorTranslator(new ObjectMapper());

    @Test
    void unErrorDeValidacionSeLeeEnteroConSusDetalles() throws Exception {
        String cuerpo = new String(new ClassPathResource("ejemplos/envios/__files/dhl-error-400.json").getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        String texto = traductor.traducir(400, cuerpo);

        assertTrue(texto.startsWith("DHL (400) Validation error: #/customerDetails/receiverDetails/postalAddress: required key [postalCode] not found"));
        assertTrue(texto.contains("\n- customerDetails.receiverDetails.postalAddress.postalCode: is missing"));
        assertTrue(texto.contains("\n- content.packages[0].weight: must be greater than 0"));
        assertFalse(texto.contains("/expressapi"), "la ruta interna de DHL no le dice nada al usuario");
    }

    @Test
    void unCuerpoQueNoEsJsonSeDevuelveRecortado() {
        String texto = traductor.traducir(502, "<html>Bad gateway</html>");

        assertEquals("DHL (502): <html>Bad gateway</html>", texto);
    }

    @Test
    void unCuerpoVacioSoloDiceElCodigo() {
        assertEquals("DHL (500): sin detalle", traductor.traducir(500, ""));
        assertEquals("DHL (500): sin detalle", traductor.traducir(500, null));
    }
}

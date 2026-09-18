package com.puntotres.packinglist.carrier.dhl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DhlLogInterceptorTest {

    @Test
    void losPdfEnBase64SeSustituyenPorSuTamano() {
        String cuerpo = "{\"documents\":[{\"imageFormat\":\"PDF\",\"content\":\"JVBERi0xLjQKZXRpcXVldGFKVkJFUmkweExqUUtaWFJwY1hWbGRHRT1KVkJFUmkweExqUUta\",\"typeCode\":\"label\"}]}";

        String limpio = DhlLogInterceptor.sinDocumentos(cuerpo);

        assertFalse(limpio.contains("JVBERi0"));
        assertTrue(limpio.contains("\"content\":\"<documento base64, 72 caracteres>\""));
        assertTrue(limpio.contains("\"typeCode\":\"label\""));
    }

    @Test
    void unContentCortoNoSeToca() {
        // "content" es también un campo legítimo corto en otras respuestas; solo se enmascara lo que parece un fichero.
        assertEquals("{\"content\":\"abc\"}", DhlLogInterceptor.sinDocumentos("{\"content\":\"abc\"}"));
    }
}

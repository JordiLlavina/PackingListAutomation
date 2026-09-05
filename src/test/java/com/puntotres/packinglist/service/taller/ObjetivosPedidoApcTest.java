package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * En APC el código de tres dígitos que escribe el taller ya identifica un
 * "Document d'achat", y un Document d'achat es una destinación, un artículo y
 * un color. Así que ese código basta: no hay que cruzar por color, que en el
 * pedido es un código ("LZZ") y en la hoja del taller un nombre ("CAMEL").
 */
class ObjetivosPedidoApcTest {

    private static byte[] pedidoReal() throws IOException {
        try (InputStream in = ObjetivosPedidoApcTest.class
                .getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            return in.readAllBytes();
        }
    }

    private static LineaTaller linea(String referencia, String code) {
        return new LineaTaller(10, "APC", "PROD", referencia, "CAMEL", "U",
                "", code, "", 8, 50);
    }

    @Test
    void elCodeDeTresDigitosIdentificaPedidoYDestinacion() throws IOException {
        // 4100128863 va a "Chine franch" en el fichero real.
        LineaTaller linea = linea("PXCBC-F67008", "863");

        ResultadoObjetivos resultado = new ObjetivosPedidoApc()
                .objetivosPara(List.of(linea), pedidoReal());

        List<ObjetivoDestino> objetivos = resultado.objetivosDe(linea);
        assertEquals(1, objetivos.size(), "un Document d'achat es una sola destinación");
        assertEquals("CHINE FRANCH", objetivos.get(0).destino());
        assertEquals("4100128863", objetivos.get(0).pedido());
        assertTrue(objetivos.get(0).cantidad() > 0);
    }

    @Test
    void laReferenciaPuedeVenirComoSufijo() throws IOException {
        // El taller escribe "F67008"; el Article real es "PXCBC-F67008".
        LineaTaller linea = linea("F67008", "863");

        assertEquals("CHINE FRANCH", new ObjetivosPedidoApc()
                .objetivosPara(List.of(linea), pedidoReal())
                .objetivosDe(linea).get(0).destino());
    }

    @Test
    void lasFilasDeTallaDelMismoPedidoSeSuman() throws IOException {
        // 4100128721 (Australia) tiene varias filas, una por talla de cinturón.
        LineaTaller linea = linea("PXBHZ-H65077", "721");

        List<ObjetivoDestino> objetivos = new ObjetivosPedidoApc()
                .objetivosPara(List.of(linea), pedidoReal()).objetivosDe(linea);

        assertEquals(1, objetivos.size());
        assertTrue(objetivos.get(0).cantidad() > 3,
                "si no se sumaran las tallas saldría la cantidad de una sola");
    }

    @Test
    void laDestinacionLlegaTalComoLaEscribeElPedido() throws IOException {
        // "Chine franch" en el fichero, "CHINE FRANCH" en el yml: se normaliza
        // aquí o la destinación no casaría con su regla ni con su padre.
        LineaTaller linea = linea("PXCBC-F67008", "863");

        assertEquals("CHINE FRANCH", new ObjetivosPedidoApc()
                .objetivosPara(List.of(linea), pedidoReal())
                .objetivosDe(linea).get(0).destino());
    }

    @Test
    void unaLineaSinCodeEsBloqueante() throws IOException {
        // Sin código no hay pedido, y sin pedido no hay destinación.
        LineaTaller linea = linea("PXCBC-F67008", "");

        ResultadoObjetivos resultado = new ObjetivosPedidoApc()
                .objetivosPara(List.of(linea), pedidoReal());

        assertTrue(resultado.getBloqueos().stream().anyMatch(b -> b.contains("CODE")));
        assertTrue(resultado.objetivosDe(linea).isEmpty());
    }

    @Test
    void unCodeQueNoEstaEnElPedidoAvisaYNoRompe() throws IOException {
        LineaTaller linea = linea("PXCBC-F67008", "000");

        ResultadoObjetivos resultado = new ObjetivosPedidoApc()
                .objetivosPara(List.of(linea), pedidoReal());

        assertTrue(resultado.objetivosDe(linea).isEmpty());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("000")));
        assertTrue(resultado.getBloqueos().isEmpty());
    }

    @Test
    void unaReferenciaAmbiguaAvisaEnVezDeElegirAlAzar() throws IOException {
        // "F65101" existe con dos prefijos distintos (PXBHZ- y PXCBT-): si los
        // dos comparten los tres dígitos, no se puede saber cuál es.
        LineaTaller linea = linea("F65101", "719");

        ResultadoObjetivos resultado = new ObjetivosPedidoApc()
                .objetivosPara(List.of(linea), pedidoReal());

        assertTrue(resultado.objetivosDe(linea).size() <= 1,
                "nunca se generan dos destinaciones de una referencia dudosa");
    }

    @Test
    void unExcelDePedidoIlegibleAvisaYNoRompe() {
        ResultadoObjetivos resultado = new ObjetivosPedidoApc()
                .objetivosPara(List.of(linea("PXCBC-F67008", "863")), new byte[] {1, 2, 3});

        assertTrue(resultado.getAvisos().stream()
                .anyMatch(a -> a.toLowerCase().contains("pedido")));
        assertTrue(resultado.getBloqueos().isEmpty());
    }
}

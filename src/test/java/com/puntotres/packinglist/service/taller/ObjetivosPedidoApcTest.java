package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.ApcPedidoExcel;

/**
 * En APC el código de tres dígitos que escribe el taller identifica un
 * "Document d'achat", que es una destinación y un artículo. El color se
 * comprueba además: el taller escribe el mismo código de color que el pedido
 * ("LAW", "GAU"), así que una fila cuyo color no es el de ese pedido no casa,
 * porque empaquetaría el artículo con el número y la destinación de otro.
 */
class ObjetivosPedidoApcTest {

    private static byte[] pedidoReal() throws IOException {
        try (InputStream in = ObjetivosPedidoApcTest.class
                .getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            return in.readAllBytes();
        }
    }

    /**
     * Una fila del taller con el color que de verdad lleva ese pedido en el
     * fichero real. El color importa: una fila cuyo color no es el del pedido
     * no casa, porque sería empaquetar el artículo con el número de otro.
     */
    private static LineaTaller linea(String referencia, String code, String color) {
        return new LineaTaller(10, "APC", "PROD", referencia, color, "U",
                "", code, "", 8, 50);
    }

    /** El pedido 4100128863 lleva LAW, KAN y CAW; basta con casar uno. */
    private static LineaTaller linea(String referencia, String code) {
        return linea(referencia, code, "LAW");
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
        LineaTaller linea = linea("PXBHZ-H65077", "721", "LZZ");

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
        LineaTaller linea = linea("F65101", "719", "LZZ");

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

    // --- El color tiene que ser el del pedido ---

    @Test
    void unColorQueNoEsElDelPedidoNoCasaYSeAvisa() throws IOException {
        // El caso real: F67080 con el pedido 701, que en el excel de APC es
        // GAU, escrito en la hoja del taller como LAW. Darlo por bueno
        // empaquetaría el artículo con el número y la destinación de otro
        // color, y eso no se ve hasta que el bulto llega al cliente.
        LineaTaller linea = linea("PXCBS-F67080", "701", "LAW");

        ResultadoObjetivos resultado = new ObjetivosPedidoApc()
                .objetivosPara(List.of(linea), pedidoReal());

        assertTrue(resultado.objetivosDe(linea).isEmpty(), "no se le asigna destinación");
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("GAU") && aviso.contains("LAW")),
                resultado.getAvisos().toString());
        assertTrue(resultado.getBloqueos().isEmpty(), "se arregla escribiendo, no bloquea");
    }

    @Test
    void elColorBuenoDeEseMismoPedidoSiCasa() throws IOException {
        // La otra mitad: con GAU, el pedido 701 se resuelve con normalidad.
        LineaTaller linea = linea("PXCBS-F67080", "701", "GAU");

        assertEquals("RETAIL", new ObjetivosPedidoApc()
                .objetivosPara(List.of(linea), pedidoReal())
                .objetivosDe(linea).get(0).destino());
    }

    @Test
    void unPedidoConVariosColoresCasaConCualquieraDeLosSuyos() throws IOException {
        // 4100128863 lleva LAW, KAN y CAW: un Document d'achat no siempre es
        // de un solo color, así que basta con casar uno.
        for (String color : List.of("LAW", "KAN", "CAW")) {
            LineaTaller linea = linea("PXCBC-F67008", "863", color);

            assertEquals("CHINE FRANCH", new ObjetivosPedidoApc()
                    .objetivosPara(List.of(linea), pedidoReal())
                    .objetivosDe(linea).get(0).destino(), color);
        }
    }

    @Test
    void elCodigoDeColorConSuNombreDetrasSigueCasando() throws IOException {
        // "GAU CAMEL": el código es lo que se compara, el nombre sobra.
        LineaTaller linea = linea("PXCBS-F67080", "701", "GAU CAMEL");

        assertEquals("RETAIL", new ObjetivosPedidoApc()
                .objetivosPara(List.of(linea), pedidoReal())
                .objetivosDe(linea).get(0).destino());
    }

    // --- APC NO propaga el número de pedido entre colores ---

    @Test
    void enApcUnaDestinacionRecibeMuchosDocumentDAchat() throws IOException {
        // Lo contrario que en AMI, donde el PO es de la referencia y la
        // destinación. Aquí un Document d'achat es una destinación, un
        // artículo Y UN COLOR, así que una destinación acumula decenas.
        // Este test existe para que nadie copie a APC el relleno automático
        // de PO de AMI: pondría en el packing list un pedido ajeno.
        // Ver la sección de números de pedido de CLAUDE.md.
        ApcPedidoExcel pedido = ApcPedidoExcel.desdeBytes(pedidoReal());

        java.util.Map<String, java.util.Set<String>> porDestino =
                new java.util.LinkedHashMap<>();
        for (String documento : java.util.List.of("4100128863", "4100128721")) {
            pedido.comandaDe(documento).ifPresent(comanda -> porDestino
                    .computeIfAbsent(comanda.destino(), clave -> new java.util.HashSet<>())
                    .add(comanda.pedido()));
        }
        assertTrue(!porDestino.isEmpty(), "los dos documentos del fichero real se leen");
    }

    @Test
    void unaFilaSinCodeNoHeredaElPedidoDeOtraFila() throws IOException {
        // La misma referencia con otro color va con OTRO Document d'achat.
        // Sin su CODE no se puede saber cuál, y adivinarlo copiando el de la
        // fila de al lado metería un pedido que no es el suyo.
        LineaTaller conCode = linea("PXCBC-F67008", "863");
        LineaTaller sinCode = new LineaTaller(11, "APC", "PROD", "PXCBC-F67008",
                "OTRO-COLOR", "U", "", "", "", 8, 50);

        ResultadoObjetivos resultado = new ObjetivosPedidoApc()
                .objetivosPara(List.of(conCode, sinCode), pedidoReal());

        assertTrue(resultado.objetivosDe(sinCode).isEmpty(),
                "en APC el pedido NO se hereda entre colores de una referencia");
        assertTrue(!resultado.objetivosDe(conCode).isEmpty(),
                "la que sí trae su CODE se resuelve con normalidad");
    }
}

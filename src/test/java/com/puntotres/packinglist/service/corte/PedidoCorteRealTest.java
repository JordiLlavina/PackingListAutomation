package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Los dos excels de pedido REALES de la temporada, que son los únicos datos
 * de cliente del repo. Las cifras se han contado sobre el fichero, no sobre
 * los JSON de ejemplo.
 */
class PedidoCorteRealTest {

    @Test
    void amiSumaCadaColorSinMirarDestinacionNiTalla() throws IOException {
        PedidoCorte pedido = new AmiCorte().leerPedido(leer("EAN PUNTOTRES H26.xlsx"));

        assertEquals(Map.of("BLACK", 169, "VANILLA CREAM", 102, "MOCHA", 31),
                bolsosDe(pedido, "ULL729.AL0103"));
        assertEquals(Map.of("BLACK", 1995, "MASTIC BEIGE", 381, "TRUFFLE", 809),
                bolsosDe(pedido, "USL728.AL0217"));
    }

    @Test
    void amiUnModeloVaEnVariasPieles() throws IOException {
        PedidoCorte pedido = new AmiCorte().leerPedido(leer("EAN PUNTOTRES H26.xlsx"));

        assertEquals(List.of("AL0137", "AL0206", "AL0218", "AL0219"), pedido.articulos().stream()
                .filter(articulo -> articulo.referencia().modelo().equals("ULL754"))
                .map(articulo -> articulo.referencia().piel())
                .toList());
    }

    @Test
    void amiSalenTodasLasReferenciasMenosLosCinturones() throws IOException {
        // Los cinturones (UBL) se cortan con otro proceso: 25 referencias, 6 de cinturón.
        PedidoCorte pedido = new AmiCorte().leerPedido(leer("EAN PUNTOTRES H26.xlsx"));

        assertEquals(19, pedido.articulos().size());
        assertTrue(pedido.articulos().stream()
                .noneMatch(articulo -> articulo.referencia().modelo().startsWith("UBL")));
        assertTrue(pedido.avisos().isEmpty(), pedido.avisos().toString());
    }

    @Test
    void amiNoTraeNombreDeBolsoYApcSiEnSuDesignacion() throws IOException {
        PedidoCorte ami = new AmiCorte().leerPedido(leer("EAN PUNTOTRES H26.xlsx"));
        PedidoCorte apc = new ApcCorte().leerPedido(leer("APC_PEDIDO_FALL26.xlsx"));

        assertTrue(ami.articulos().stream().allMatch(articulo -> articulo.nombreModelo().isEmpty()));
        assertTrue(apc.articulos().stream().allMatch(articulo -> !articulo.nombreModelo().isBlank()));
        assertEquals("le neige clou", apc.articulos().stream()
                .filter(articulo -> articulo.referencia().modelo().equals("F67043"))
                .findFirst().orElseThrow().nombreModelo().toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    void apcSumaCadaColorYPartePorElGuion() throws IOException {
        PedidoCorte pedido = new ApcCorte().leerPedido(leer("APC_PEDIDO_FALL26.xlsx"));

        assertEquals(Map.of("LZZ", 569, "LAW", 200, "KAN", 111, "CAW", 97),
                bolsosDe(pedido, "PXCBC-F67008"));
        assertEquals(List.of("PXCBC", "PXCDS"), pedido.articulos().stream()
                .filter(articulo -> articulo.referencia().modelo().equals("F67008"))
                .map(articulo -> articulo.referencia().piel())
                .toList());
        assertEquals(16, pedido.articulos().size());
        assertTrue(pedido.avisos().isEmpty(), pedido.avisos().toString());
    }

    @Test
    void elServicioSoloConoceAmiYApc() {
        DocumentosCorteService servicio = new DocumentosCorteService(
                List.of(new AmiCorte(), new ApcCorte()));

        assertTrue(servicio.clientePara("ami").isPresent());
        assertTrue(servicio.clientePara("APC").isPresent());
        assertTrue(servicio.clientePara("ACKERMANN").isEmpty());
        assertTrue(servicio.clientePara(null).isEmpty());
    }

    private static Map<String, Integer> bolsosDe(PedidoCorte pedido, String referencia) {
        Map<String, Integer> bolsos = new LinkedHashMap<>();
        pedido.articulos().stream()
                .filter(articulo -> articulo.referencia().referencia().equals(referencia))
                .findFirst().orElseThrow()
                .colores().forEach(color -> bolsos.put(color.color(), color.bolsos()));
        return bolsos;
    }

    private static byte[] leer(String fichero) throws IOException {
        try (InputStream entrada = PedidoCorteRealTest.class.getResourceAsStream("/ejemplos/" + fichero)) {
            return entrada.readAllBytes();
        }
    }
}

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

        assertEquals(Map.of("001 BLACK", 169, "718 VANILLA CREAM", 102, "A237 MOCHA", 31),
                bolsosDe(pedido, "ULL729.AL0103"));
        assertEquals(Map.of("001 BLACK", 1995, "A184 MASTIC BEIGE", 381, "A236 TRUFFLE", 809),
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

    /**
     * Spring 27 ya no trae la columna Couleurs: el color sale del sku. Sin
     * eso los colores de cada referencia se sumaban en una sola ficha sin
     * color (PXCEV-F63034 salía como 254 bolsos de nada).
     */
    @Test
    void apcSpring27SacaElColorDelSkuYNoJuntaLosColores() throws IOException {
        PedidoCorte pedido = new ApcCorte().leerPedido(leer("APC_PEDIDO_SPRING27.xlsx"));

        assertEquals(Map.of("LZZ", 105, "GAA", 149), bolsosDe(pedido, "PXCEV-F63034"));
        assertEquals(22, pedido.articulos().size());
        assertTrue(pedido.articulos().stream().flatMap(articulo -> articulo.colores().stream())
                .noneMatch(color -> color.codigo().isEmpty()));
        assertEquals(java.util.Set.of("CAD", "CAE", "CAV", "DAB", "FAB", "GAA", "IAI", "IBC",
                        "KBG", "LAO", "LZZ", "TLD"),
                new ApcCorte().coloresDelPedido(leer("APC_PEDIDO_SPRING27.xlsx")).keySet());
    }

    @Test
    void amiTraeCodigoYDescripcionDelColorYApcSoloElCodigo() throws IOException {
        // AMI: COLORIS + Libellé coloris, así que no necesita la Tabla colores
        // Clientes. APC: solo Couleurs, y sus nueve códigos son los que se
        // siembran en la tabla (TablaColoresTest lo comprueba por su lado).
        PedidoCorte ami = new AmiCorte().leerPedido(leer("EAN PUNTOTRES H26.xlsx"));
        PedidoCorte apc = new ApcCorte().leerPedido(leer("APC_PEDIDO_FALL26.xlsx"));

        assertTrue(ami.codigosSinDescripcion().isEmpty());
        assertTrue(ami.articulos().stream().flatMap(articulo -> articulo.colores().stream())
                .allMatch(color -> !color.codigo().isEmpty() && !color.descripcion().isEmpty()));
        assertEquals(java.util.Set.of("CAB", "CAE", "CAW", "GAE", "GAU", "KAN", "KBE", "LAW", "LZZ"),
                apc.codigosSinDescripcion());
    }

    @Test
    void losColoresDelPedidoSonTodosTambienLosDeLosCinturones() throws IOException {
        // Para la Tabla colores Clientes: 13 colores en el pedido de AMI, 4 de
        // ellos solo en cinturones (que el corte deja fuera), y los 9 de APC.
        Map<String, String> ami = new AmiCorte().coloresDelPedido(leer("EAN PUNTOTRES H26.xlsx"));
        Map<String, String> apc = new ApcCorte().coloresDelPedido(leer("APC_PEDIDO_FALL26.xlsx"));

        assertEquals(13, ami.size(), ami.toString());
        assertEquals("BLACK", ami.get("001"));
        assertEquals("NOIR/ARGENT VIBRE", ami.get("0014"), "un color que solo llevan los cinturones");
        assertTrue(ami.values().stream().noneMatch(String::isEmpty));
        assertEquals(java.util.Set.of("CAB", "CAE", "CAW", "GAE", "GAU", "KAN", "KBE", "LAW", "LZZ"), apc.keySet());
        assertTrue(apc.values().stream().allMatch(String::isEmpty), "APC no trae la descripción");
    }

    @Test
    void lasPielesDelPedidoSonLaParteDeLaReferenciaQueEsLaPiel() throws IOException {
        java.util.Set<String> ami = new AmiCorte().pielesDelPedido(leer("EAN PUNTOTRES H26.xlsx"));
        java.util.Set<String> apc = new ApcCorte().pielesDelPedido(leer("APC_PEDIDO_FALL26.xlsx"));

        assertTrue(ami.contains("AL0103") && ami.contains("AL0216"), ami.toString());
        assertTrue(ami.stream().allMatch(piel -> piel.startsWith("AL")), "detrás del punto: " + ami);
        assertTrue(apc.contains("PXCBC") && apc.contains("PXCDS"), apc.toString());
        assertTrue(apc.stream().allMatch(piel -> piel.startsWith("PX")), "delante del guion: " + apc);
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
                .colores().forEach(color -> bolsos.put(color.nombre(), color.bolsos()));
        return bolsos;
    }

    private static byte[] leer(String fichero) throws IOException {
        try (InputStream entrada = PedidoCorteRealTest.class.getResourceAsStream("/ejemplos/" + fichero)) {
            return entrada.readAllBytes();
        }
    }
}

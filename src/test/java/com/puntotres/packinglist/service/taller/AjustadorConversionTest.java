package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.DestinoClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres.Aviso;
import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres.Fila;
import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres.Resumen;

/**
 * Lo que Java decide tras la conversión, contra el pedido real de APC
 * (Fall 26) y el de AMI (H26): traducir el color con la tabla, completar la
 * referencia y resolver el código de pedido solo cuando es único, y
 * contrastar los totales. Nada de esto lo hace el modelo.
 */
class AjustadorConversionTest {

    private static PedidoParaConversion apc;
    private static PedidoParaConversion ami;

    @BeforeAll
    static void cargarPedidos() throws Exception {
        apc = PedidoParaConversion.cargar(TipoPlantilla.APC, leer("/ejemplos/APC_PEDIDO_FALL26.xlsx"),
                Map.of("LZZ", "NOIR", "LAW", "GRIS", "KAN", ""), clienteApc());
        ami = PedidoParaConversion.cargar(TipoPlantilla.AMI, leer("/ejemplos/EAN PUNTOTRES H26.xlsx"),
                null, null);
    }

    private static byte[] leer(String recurso) throws Exception {
        try (InputStream in = AjustadorConversionTest.class.getResourceAsStream(recurso)) {
            return in.readAllBytes();
        }
    }

    /** El catálogo de destinos de APC como en el yml: lo justo para resolver padres. */
    private static ClienteConfig clienteApc() {
        ClienteConfig cliente = new ClienteConfig();
        DestinoClienteConfig wholesale = new DestinoClienteConfig();
        wholesale.setAbreviatura("WH");
        wholesale.setDestinosHijo(List.of("AUSTRALIA", "WHOLESALE", "CHINE FRANCH"));
        DestinoClienteConfig usa = new DestinoClienteConfig();
        usa.setAbreviatura("UST");
        usa.setDestinosHijo(List.of("DOUANES USA", "D. USA", "RETAIL USA"));
        DestinoClienteConfig retail = new DestinoClienteConfig();
        retail.setAbreviatura("RT");
        retail.setDestinosHijo(List.of("RETAIL", "WHOLESALE CONCESS"));
        DestinoClienteConfig japan = new DestinoClienteConfig();
        japan.setAbreviatura("JPT");
        DestinoClienteConfig korea = new DestinoClienteConfig();
        korea.setAbreviatura("KRT");
        cliente.setDestinos(Map.of("WHOLESALE", wholesale, "D. USA", usa, "RETAIL", retail,
                "JAPAN", japan, "KOREA", korea));
        return cliente;
    }

    private static Fila fila(String cliente, String referencia, String color, String destino,
                             String code, Integer cantidad, String cajas) {
        return new Fila(null, cliente, null, referencia, color, "U", destino, code, 10, cantidad,
                cajas, null, null);
    }

    private static PackingFormatoPuntotres packing(Resumen resumen, Fila... filas) {
        return new PackingFormatoPuntotres("H", List.of(), null, List.of(filas), List.of(), resumen,
                List.of());
    }

    private static String avisos(PackingFormatoPuntotres packing) {
        return String.join("\n", packing.avisos().stream().map(Aviso::frase).toList());
    }

    // --- APC ---

    /** Fez: "F63023 LAW GRIS USA" sin código. En el pedido solo PXCBC-F63023 711 es LAW a Douanes USA. */
    @Test
    void sinCodigoSeResuelveElPedidoSoloCuandoEsUnico() {
        PackingFormatoPuntotres ajustado = AjustadorConversion.ajustar(packing(null,
                fila("APC", "F63023", "GRIS", "DOUANES USA", null, 9, "77")), apc);

        Fila fila = ajustado.filas().get(0);
        assertEquals("LAW", fila.color(), "el nombre pasa a código por la tabla");
        assertEquals("711", fila.codigoCliente());
        assertEquals("PXCBC-F63023", fila.referencia());
        String avisos = avisos(ajustado);
        assertTrue(avisos.contains("Fila 1: El color 'GRIS'"), avisos);
        assertTrue(avisos.contains("Fila 1: Código de pedido 711 (4100128711)"), avisos);
    }

    /** F67066 solo está pedido en LZZ para Wholesale: a Japón no hay nada. */
    @Test
    void sinNingunPedidoQueCaseSeQuedaSinCodigoYAvisa() {
        PackingFormatoPuntotres ajustado = AjustadorConversion.ajustar(packing(null,
                fila("APC", "F67066", "LZZ", "JAPAN", null, 44, "85-88")), apc);

        assertNull(ajustado.filas().get(0).codigoCliente());
        assertTrue(avisos(ajustado).contains("no hay ninguna línea de F67066 en LZZ para JAPAN"),
                avisos(ajustado));
    }

    @Test
    void conVariosPedidosPosiblesNoSeEligeNinguno() {
        // F67043 en LAW a Korea: 691 (PXCBC) y 693 (PXCDS).
        PackingFormatoPuntotres ajustado = AjustadorConversion.ajustar(packing(null,
                fila("APC", "F67043", "LAW", "KOREA", null, 40, "90-93")), apc);

        assertNull(ajustado.filas().get(0).codigoCliente());
        assertEquals("F67043", ajustado.filas().get(0).referencia());
        assertTrue(avisos(ajustado).contains("varios números"), avisos(ajustado));
    }

    /**
     * Tánger: "67008 Noir Korea 690". Con el código, la referencia se
     * completa y el color se comprueba. Java solo traduce el nombre EXACTO
     * de la tabla ("noir"); pasar "Negro" a "NOIR" es cosa del modelo.
     */
    @Test
    void conCodigoSeCompletaLaReferenciaYSeComparaElColor() {
        // Medido en el fichero real: el pedido 4100128690 lleva LZZ y LAW
        // (Claude, en las pruebas reales, dijo dos veces que solo LZZ: por
        // eso este cruce lo hace Java). KAN sí es un conflicto.
        PackingFormatoPuntotres ajustado = AjustadorConversion.ajustar(packing(null,
                fila("APC", "67008", "noir", "KOREA", "690", 30, "1-3"),
                fila("APC", "67008", "Gris", "KOREA", "690", 40, "28-31"),
                fila("APC", "67008", "KAN", "KOREA", "690", 10, "32")), apc);

        Fila negro = ajustado.filas().get(0);
        assertEquals("PXCBC-F67008", negro.referencia());
        assertEquals("LZZ", negro.color());
        assertEquals("690", negro.codigoCliente());

        Fila gris = ajustado.filas().get(1);
        assertEquals("LAW", gris.color());
        assertEquals("PXCBC-F67008", gris.referencia());

        Fila liquen = ajustado.filas().get(2);
        assertEquals("KAN", liquen.color(), "el conflicto se avisa, no se tapa");
        assertEquals("690", liquen.codigoCliente(), "se deja lo escrito");
        String avisos = avisos(ajustado);
        assertTrue(avisos.contains("Fila 3: El pedido 4100128690 (código 690) es en LZZ/LAW y la "
                + "hoja del taller dice KAN"), avisos);
        assertFalse(avisos.contains("Fila 2: El pedido"), avisos);
    }

    @Test
    void unCodigoQueNoExisteParaEsaReferenciaAvisa() {
        PackingFormatoPuntotres ajustado = AjustadorConversion.ajustar(packing(null,
                fila("APC", "67008", "LZZ", "KOREA", "999", 30, "1-3")), apc);
        assertEquals("67008", ajustado.filas().get(0).referencia());
        assertTrue(avisos(ajustado).contains("no hay ninguna línea de 67008 con el código 999"),
                avisos(ajustado));
    }

    /** Un nombre que la tabla no conoce se deja, y se pide UNA vez que se añada. */
    @Test
    void unColorQueNoEstaEnLaTablaSePideUnaSolaVez() {
        PackingFormatoPuntotres ajustado = AjustadorConversion.ajustar(packing(null,
                fila("APC", "F67043", "CEREZA", "JAPAN", null, 20, "95-96"),
                fila("APC", "F67043", "CEREZA", "RETAIL", null, 96, "103-110")), apc);

        assertEquals("CEREZA", ajustado.filas().get(0).color());
        assertNull(ajustado.filas().get(0).codigoCliente());
        List<Aviso> deColor = ajustado.avisos().stream()
                .filter(a -> a.texto().contains("'CEREZA'")).toList();
        assertEquals(1, deColor.size(), avisos(ajustado));
        assertTrue(deColor.get(0).texto().contains("(filas 1, 2)"), deColor.get(0).texto());
        assertTrue(deColor.get(0).texto().contains("Colores y Pieles de Cliente"));
    }

    @Test
    void lasFilasDeOtrosClientesNoSeTocan() {
        PackingFormatoPuntotres ajustado = AjustadorConversion.ajustar(packing(null,
                fila("AMI", "ULL163AL052", "MARRON", "PA", null, 345, "1-69")), apc);
        assertEquals("ULL163AL052", ajustado.filas().get(0).referencia());
        assertEquals("MARRON", ajustado.filas().get(0).color());
        assertTrue(ajustado.avisos().isEmpty(), avisos(ajustado));
    }

    // --- AMI ---

    @Test
    void amiCasaLaReferenciaEscritaSinPuntosNiCeros() {
        PackingFormatoPuntotres ajustado = AjustadorConversion.ajustar(packing(null,
                fila("AMI", "ULL163AL052", "DARK COFFEE", "PARIS", null, 345, "1-69")), ami);

        assertEquals("ULL163.AL0052", ajustado.filas().get(0).referencia());
        assertTrue(avisos(ajustado).contains("Fila 1: Referencia escrita como ULL163AL052"),
                avisos(ajustado));
    }

    @Test
    void amiAvisaDeUnColorQueNoEsDeLaReferencia() {
        PackingFormatoPuntotres ajustado = AjustadorConversion.ajustar(packing(null,
                fila("AMI", "ULL163.AL0052", "MARRON", "PARIS", null, 345, "1-69")), ami);
        assertTrue(avisos(ajustado).contains("El color 'MARRON' no es ninguno de los de ULL163.AL0052"),
                avisos(ajustado));
    }

    // --- Totales ---

    @Test
    void losTotalesQueDeclaraLaHojaSeContrastanConLasFilas() {
        PackingFormatoPuntotres ajustado = AjustadorConversion.ajustar(packing(new Resumen(60, 6),
                fila("APC", "PXCBC-F67008", "LZZ", "KOREA", "690", 30, "1-3"),
                fila("APC", "PXCBC-F67008", "LZZ", "KOREA", "690", 28, "4-5")), apc);

        String avisos = avisos(ajustado);
        assertTrue(avisos.contains("declara 60 unidades y las filas convertidas suman 58"), avisos);
        assertTrue(avisos.contains("declara 6 cajas y las filas convertidas suman 5"), avisos);
    }

    @Test
    void sinResumenNoHayNadaQueContrastar() {
        assertTrue(AjustadorConversion.contrastarTotales(packing(null,
                fila("APC", "PXCBC-F67008", "LZZ", "KOREA", "690", 30, "1-3"))).isEmpty());
        assertEquals(3, fila("APC", "X", "LZZ", "KOREA", null, 1, "1-2, 7").numeroDeCajas());
        assertEquals(0, fila("APC", "X", "LZZ", "KOREA", null, 1, null).numeroDeCajas());
    }

    /** Fez: la caja 76 lleva 11 de un color y 1 de otro (celda combinada). Es UNA caja. */
    @Test
    void unaCajaMixtaCuentaUnaVezEnElTotalDeCajas() {
        PackingFormatoPuntotres packing = packing(new Resumen(null, 3),
                fila("APC", "PXCBC-F63023", "LZZ", "WHOLESALE", "722", 11, "75-76"),
                fila("APC", "PXCBC-F63023", "LZZ", "DOUANES USA", "709", 1, "76"),
                fila("APC", "PXCBC-F63023", "LAW", "DOUANES USA", "711", 9, "77"));
        assertEquals(3, packing.cajasConvertidas());
        assertTrue(AjustadorConversion.contrastarTotales(packing).isEmpty());
    }

    /** Lo unívoco es informativo; lo que no cuadra pide revisión. */
    @Test
    void loCompletadoEsInformativoYLoQueNoCuadraPideRevision() {
        PackingFormatoPuntotres ajustado = AjustadorConversion.ajustar(packing(null,
                fila("APC", "67008", "noir", "KOREA", "690", 30, "1-3"),
                fila("APC", "67008", "KAN", "KOREA", "690", 10, "32")), apc);

        List<String> informativos = ajustado.avisosInformativos().stream().map(Aviso::frase).toList();
        List<String> revisar = ajustado.avisosParaRevisar().stream().map(Aviso::frase).toList();
        assertTrue(informativos.stream().anyMatch(a -> a.startsWith("Fila 1: El color 'noir'")),
                informativos.toString());
        assertTrue(informativos.stream().anyMatch(a -> a.startsWith("Fila 1: Referencia completada")),
                informativos.toString());
        assertEquals(1, revisar.size(), revisar.toString());
        assertTrue(revisar.get(0).startsWith("Fila 2: El pedido 4100128690"), revisar.toString());
    }

}

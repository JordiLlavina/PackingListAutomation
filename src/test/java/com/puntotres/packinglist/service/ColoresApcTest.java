package com.puntotres.packinglist.service;

import static com.puntotres.packinglist.testutil.TestDatos.caja;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DestinoData;

/**
 * El color de APC sale como lo escribe el cliente, código-nombre, con la
 * tabla de colores; lo que no se reconoce se queda como llegó y se avisa.
 * La tabla de aquí es de prueba: sus nombres no son los del cliente.
 */
class ColoresApcTest {

    private static final Map<String, String> TABLA = tabla();

    private static Map<String, String> tabla() {
        Map<String, String> colores = new LinkedHashMap<>();
        colores.put("LZZ", "Noir");
        colores.put("LAW", "GRIS");
        colores.put("GAU", "CAMEL");
        colores.put("KAN", "");
        return colores;
    }

    private static byte[] pedidoReal() throws IOException {
        try (InputStream in = ColoresApcTest.class
                .getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            return in.readAllBytes();
        }
    }

    private static List<EnvioImportado.DestinoImportado> envioCon(CajaData... cajas) {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("WHOLESALE");
        destino.setCajas(new ArrayList<>(List.of(cajas)));
        return List.of(new EnvioImportado.DestinoImportado(destino, List.of()));
    }

    private static CajaData conColor(String color) {
        return caja(1, null, "PXCBC-F63023", color, 3, null, 4.2);
    }

    @Test
    void elCodigoSaleConElNombreDeLaTablaLoEscribanComoLoEscriban() {
        CajaData soloCodigo = conColor("lzz");
        CajaData codigoYOtroNombre = conColor("LZZ Negro");
        CajaData yaEnSuForma = conColor("LZZ-NOIR");
        CajaData elNombreDeLaTabla = conColor("camel");

        List<String> avisos = ColoresApc.normalizar(envioCon(soloCodigo, codigoYOtroNombre,
                yaEnSuForma, elNombreDeLaTabla), TABLA, null);

        assertEquals("LZZ-NOIR", soloCodigo.getCodigoColor());
        // Manda el nombre de la tabla, no el que escribió la operaria.
        assertEquals("LZZ-NOIR", codigoYOtroNombre.getCodigoColor());
        assertEquals("LZZ-NOIR", yaEnSuForma.getCodigoColor());
        assertEquals("GAU-CAMEL", elNombreDeLaTabla.getCodigoColor());
        assertTrue(avisos.isEmpty(), avisos.toString());
    }

    /** El parecido es cosa del modelo: Java no adivina que "CLOU CAMEL" es GAU. */
    @Test
    void loQueNoSeReconoceSeQuedaComoLlegoYSeAvisaUnaVez() {
        CajaData una = conColor("CLOU CAMEL");
        CajaData otra = conColor("clou camel");

        List<String> avisos = ColoresApc.normalizar(envioCon(una, otra), TABLA, null);

        assertEquals("CLOU CAMEL", una.getCodigoColor());
        assertEquals(1, avisos.size(), avisos.toString());
        assertTrue(avisos.get(0).contains("'CLOU CAMEL'"), avisos.get(0));
        assertTrue(avisos.get(0).contains("Colores y Pieles de Cliente"), avisos.get(0));
    }

    /**
     * Un código sin nombre en la tabla sale solo y se pide el nombre; pero
     * si ya venía con uno escrito detrás no se le quita.
     */
    @Test
    void unCodigoSinNombreEnLaTablaNoPisaElQueVengaEscrito() {
        CajaData solo = conColor("KAN");
        CajaData conNombre = conColor("KAN-LIQUEN");

        List<String> avisos = ColoresApc.normalizar(envioCon(solo, conNombre), TABLA, null);

        assertEquals("KAN", solo.getCodigoColor());
        assertEquals("KAN-LIQUEN", conNombre.getCodigoColor());
        assertEquals(1, avisos.size(), avisos.toString());
        assertTrue(avisos.get(0).startsWith("Colores sin nombre en la tabla de colores de APC: KAN."),
                avisos.get(0));
    }

    /**
     * Medido en el pedido real: 4100128711 es PXCBC-F63023 en LAW. Si el
     * envío dice otro color, o el color o el pedido están mal, y se avisa una
     * vez aunque sean varias cajas. El color leído no se cambia.
     */
    @Test
    void elColorSeCompruebaContraElDocumentDAchatDeLaCaja() throws IOException {
        // El color equivocado se elige mirando el pedido, no suponiéndolo: un
        // Document d'achat puede llevar varios colores.
        java.util.Set<String> delPedido =
                ApcPedidoExcel.desdeBytes(pedidoReal()).coloresDe("4100128711");
        assertTrue(delPedido.contains("LAW"), delPedido.toString());
        String otro = delPedido.contains("GAU") ? "LZZ" : "GAU";
        assertTrue(!delPedido.contains(otro), delPedido.toString());

        CajaData bien = caja(1, "4100128711", "PXCBC-F63023", "LAW", 3, null, 4.2);
        CajaData mal = caja(2, "4100128711", "PXCBC-F63023", otro, 3, null, 4.2);
        CajaData malOtraVez = caja(3, "4100128711", "PXCBC-F63023", otro, 3, null, 4.2);

        List<String> avisos = ColoresApc.normalizar(envioCon(bien, mal, malOtraVez), TABLA,
                pedidoReal());

        assertEquals("LAW-GRIS", bien.getCodigoColor());
        assertEquals(otro + "-" + TABLA.get(otro).toUpperCase(), mal.getCodigoColor());
        assertEquals(1, avisos.size(), avisos.toString());
        assertTrue(avisos.get(0).startsWith("El pedido 4100128711 de PXCBC-F63023 es en "),
                avisos.get(0));
        assertTrue(avisos.get(0).contains("y el envío dice " + otro), avisos.get(0));
    }

    /** Un código que solo conoce el pedido (no está en la tabla) se reconoce igual. */
    @Test
    void unCodigoDelPedidoQueNoEstaEnLaTablaSeReconoce() throws IOException {
        CajaData linea = caja(1, "4100128711", "PXCBC-F63023", "LAW gris", 3, null, 4.2);

        List<String> avisos = ColoresApc.normalizar(envioCon(linea), Map.of(), pedidoReal());

        // Sin nombre en la tabla no se toca lo escrito, y no hay nada que avisar:
        // el código está y el nombre lo puso quien lo escribió.
        assertEquals("LAW gris", linea.getCodigoColor());
        assertTrue(avisos.isEmpty(), avisos.toString());
    }
}

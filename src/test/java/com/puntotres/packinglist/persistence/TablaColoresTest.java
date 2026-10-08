package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.service.corte.ApcCorte;

/**
 * La Tabla colores Clientes. Cada test usa su propio cliente o sus propios
 * códigos: la base de datos en memoria es la misma para toda la suite.
 */
@SpringBootTest
class TablaColoresTest {

    @Autowired
    private TablaColores tabla;

    @Test
    void laSemillaSonLosCodigosDelPedidoRealDeApcYSoloLzzLlevaDescripcion() throws IOException {
        // "Tantos colores como siglas haya en el pedido de APC": se cuenta sobre
        // el fichero real, no se copia a mano.
        byte[] pedido;
        try (InputStream entrada = getClass().getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            pedido = entrada.readAllBytes();
        }
        assertEquals(new ApcCorte().leerPedido(pedido).codigosSinDescripcion(),
                TablaColores.SEMILLA.get("APC").keySet());
        assertEquals("NOIR", TablaColores.SEMILLA.get("APC").get("LZZ"));
        assertEquals(8, TablaColores.SEMILLA.get("APC").values().stream().filter(String::isEmpty).count(),
                "las descripciones que no se saben no se inventan");
    }

    @Test
    void alArrancarConLaTablaVaciaSeSiembra() {
        assertEquals("NOIR", tabla.buscar("APC", "LZZ").orElseThrow().getDescripcion());
        assertEquals("", tabla.buscar("APC", "CAB").orElseThrow().getDescripcion());
    }

    @Test
    void seGuardaSeCorrigeYSeBorraConElCodigoNormalizado() {
        tabla.guardar("PRUEBA1", " ab1 ", "Rojo");
        assertEquals("Rojo", tabla.buscar("prueba1", "AB1").orElseThrow().getDescripcion());

        tabla.guardar("PRUEBA1", "AB1", " Rouge ");
        assertEquals(List.of("AB1"), tabla.de("PRUEBA1").stream().map(ColorCliente::getCodigo).toList());
        assertEquals("Rouge", tabla.de("PRUEBA1").get(0).getDescripcion());

        assertTrue(tabla.borrar("PRUEBA1", "ab1"));
        assertFalse(tabla.borrar("PRUEBA1", "AB1"), "ya no había nada que borrar");
        assertTrue(tabla.de("PRUEBA1").isEmpty());
    }

    @Test
    void consultarDaLasDescripcionesYAnadeVaciosLosCodigosQueNoEstaban() {
        tabla.guardar("PRUEBA2", "ZZ1", "NOIR");
        tabla.guardar("PRUEBA2", "ZZ2", "");

        TablaColores.Consulta consulta = tabla.consultar("PRUEBA2", List.of("zz1", "ZZ2", "ZZ3"));

        assertEquals(Map.of("ZZ1", "NOIR", "ZZ2", "", "ZZ3", ""), consulta.descripciones());
        assertEquals(List.of("ZZ3"), consulta.anadidos());
        assertEquals(List.of("ZZ2", "ZZ3"), consulta.sinDescripcion());
        assertTrue(tabla.buscar("PRUEBA2", "ZZ3").isPresent(), "queda en la tabla para rellenarlo");

        assertTrue(tabla.consultar("PRUEBA2", List.of("ZZ3")).anadidos().isEmpty(),
                "la segunda vez ya estaba");
    }

    @Test
    void incorporarAnadeLosNuevosRellenaLosHuecosYNoPisaLoEscrito() {
        tabla.guardar("PRUEBA3", "Y2", "");
        tabla.guardar("PRUEBA3", "Y3", "PUESTO A MANO");
        Map<String, String> delPedido = new java.util.LinkedHashMap<>();
        delPedido.put("Y1", "BLACK");
        delPedido.put("Y2", "VANILLA CREAM");
        delPedido.put("Y3", "OTRO");
        delPedido.put("Y4", "");

        Incorporacion incorporacion = tabla.incorporar("PRUEBA3", delPedido);

        assertEquals(List.of("Y1", "Y4"), incorporacion.anadidos());
        assertEquals(List.of("Y2"), incorporacion.completados());
        assertEquals(List.of("Y3"), incorporacion.yaEstaban());
        assertEquals(4, incorporacion.total());
        assertEquals("BLACK", tabla.buscar("PRUEBA3", "Y1").orElseThrow().getDescripcion());
        assertEquals("VANILLA CREAM", tabla.buscar("PRUEBA3", "Y2").orElseThrow().getDescripcion());
        assertEquals("PUESTO A MANO", tabla.buscar("PRUEBA3", "Y3").orElseThrow().getDescripcion());
        assertEquals("", tabla.buscar("PRUEBA3", "Y4").orElseThrow().getDescripcion());
    }
}

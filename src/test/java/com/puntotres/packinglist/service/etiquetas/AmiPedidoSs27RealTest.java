package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Ancla la lectura del excel de pedido REAL de AMI de la temporada SS27
 * ("EAN PUNTOTRES SS27.xlsx", copia del de docs/Etiquetas cajas/AMI
 * documentos test/), que llegó con otro formato que el H26: la hoja se llama
 * "Feuil1 Original", las cabeceras son "P.O.", " DESCRIPTION COLORIS",
 * "EAN 13 " y "CODE 128", y no hay columna Made in. {@link AmiPedidoRealTest}
 * sigue anclando el H26: los dos formatos tienen que leerse.
 */
class AmiPedidoSs27RealTest {

    private static AmiPedidoExcel pedido;

    @BeforeAll
    static void cargarElPedidoReal() throws IOException {
        try (InputStream entrada = AmiPedidoSs27RealTest.class
                .getResourceAsStream("/ejemplos/EAN PUNTOTRES SS27.xlsx")) {
            assertNotNull(entrada, "Falta src/test/resources/ejemplos/EAN PUNTOTRES SS27.xlsx");
            pedido = AmiPedidoExcel.desdeBytes(entrada.readAllBytes());
        }
    }

    /**
     * Sin columna Made in el país sale del final del Code 128, así que no
     * falta nada: un aviso aquí saldría en todos los envíos de la temporada.
     */
    @Test
    void elFicheroRealTraeTodoLoQueHaceFalta() {
        assertTrue(pedido.avisos().isEmpty(), pedido.avisos().toString());
    }

    @Test
    void cinturonDeFrance() {
        // Fila 2: UBL029.AL0246 / 0014 / 75 / 09006 (sin sufijo = France).
        AmiPedidoExcel.FilaPedido fila =
                pedido.buscar("UBL029.AL0246", "0014", "75", null).orElseThrow();
        assertEquals("09006", fila.orderNumber());
        assertEquals("0014", fila.colorCode());
        assertEquals("0014 NOIR/ARGENT VIBRE", fila.colorCompleto());
        assertEquals("3667549161264", fila.ean13());
        assertEquals("366754916126400001000090060000000000000000ES", fila.ean128());
        assertTrue(fila.avisosEan().isEmpty(), fila.avisosEan().toString());
    }

    /** Fila 68: el bolso de PRESS, hecho en Marruecos. */
    @Test
    void bolsoDePress() {
        AmiPedidoExcel.FilaPedido fila =
                pedido.buscar("ULL770.AL0245", "001", null, "PRESS").orElseThrow();
        assertEquals("09839", fila.orderNumber());
        assertEquals("3667549163510", fila.ean13());
        assertEquals("366754916351000001000098390000000000000000MA", fila.ean128());
        assertTrue(fila.avisosEan().isEmpty(), fila.avisosEan().toString());
    }

    /** El nombre del color sale de " DESCRIPTION COLORIS": casa por nombre. */
    @Test
    void elColorCasaPorSuNombre() {
        assertEquals("09839",
                pedido.buscar("ULL770.AL0245", "NOIR", null, "PRESS").orElseThrow().orderNumber());
    }

    @Test
    void lasDestinacionesDelPedidoSonChinaJapanYPress() {
        assertEquals(Set.of("CH", "JP", "PRESS"), new HashSet<>(pedido.sufijosPo()));
    }

    /**
     * Ninguna fila del fichero real tiene un Code 128 que contradiga su EAN13
     * y su PO (medido sobre las 100): ni bolsos (MA) ni cinturones (ES).
     */
    @Test
    void ningunCodigo128ContradiceSuFila() {
        assertTrue(pedido.buscar("USL745.AL0245", "709", null, "CH").orElseThrow()
                .avisosEan().isEmpty());
        assertTrue(pedido.buscar("UBL218.AL0238", "A519", "85", "PRESS").orElseThrow()
                .avisosEan().isEmpty());
    }
}

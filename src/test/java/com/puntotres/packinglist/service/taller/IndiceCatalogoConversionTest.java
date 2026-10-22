package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.TipoPlantilla;

/**
 * El índice del pedido que viaja a Claude, contra los ficheros reales: tiene
 * que estar indexado por lo que escribe el taller (sufijo, nombre de color,
 * código de tres cifras) y no llevar cantidades.
 */
class IndiceCatalogoConversionTest {

    private static byte[] leer(String recurso) throws Exception {
        try (InputStream in = IndiceCatalogoConversionTest.class.getResourceAsStream(recurso)) {
            return in.readAllBytes();
        }
    }

    @Test
    void apcVaAgrupadoPorSufijoConSusLineasDePedidoYLaTablaDeColores() throws Exception {
        PedidoParaConversion pedido = PedidoParaConversion.cargar(TipoPlantilla.APC,
                leer("/ejemplos/APC_PEDIDO_FALL26.xlsx"),
                Map.of("LZZ", "NOIR", "LAW", "GRIS"), null);

        String texto = IndiceCatalogoConversion.para(pedido).texto();

        assertTrue(texto.contains("## Pedido de APC esta temporada (16 artículos"), texto);
        assertTrue(texto.contains("LZZ = NOIR"), texto);
        assertTrue(texto.contains("LAW = GRIS"), texto);
        assertTrue(texto.contains("KAN = " + IndiceCatalogoConversion.SIN_NOMBRE), texto);
        // El sufijo como lo escribe el taller, con sus artículos debajo.
        assertTrue(texto.contains("\nF67008\n  PXC"), texto);
        assertTrue(texto.contains("  PXCBC-F67008 \"le neige\" — colores "), texto);
        assertTrue(texto.contains("  PXCDS-F67008 \"le neige\" — colores "), texto);
        // Cada línea de pedido: código de 3 cifras, destinación, color.
        assertTrue(texto.contains("690 Korea LZZ"), texto);
        assertTrue(texto.contains("863 Chine franch LAW"), texto);
        // Sin cantidades: ninguna línea lleva un número suelto fuera del código.
        assertFalse(texto.matches("(?s).*Korea LZZ \\d+.*"), texto);
    }

    @Test
    void amiVaPorReferenciaConSuFormaCortaSusColoresYSusPos() throws Exception {
        PedidoParaConversion pedido = PedidoParaConversion.cargar(TipoPlantilla.AMI,
                leer("/ejemplos/EAN PUNTOTRES H26.xlsx"), null, null);

        String texto = IndiceCatalogoConversion.para(pedido).texto();

        assertTrue(texto.contains("## Pedido de AMI esta temporada"), texto);
        assertTrue(texto.contains("ULL163.AL0052 (ULL163AL52)"), texto);
        assertTrue(texto.matches("(?s).*ULL163\\.AL0052 \\(ULL163AL52\\) \\| [^|]*= [^|]*\\|.*"),
                "los colores van como código = nombre: " + texto);
        assertTrue(texto.contains(" CH"), texto);
    }

    @Test
    void laFormaCortaQuitaPuntosYCerosDeCadaGrupoDeCifras() {
        assertEquals("ULL163AL52", IndiceCatalogoConversion.formaCorta("ULL163.AL0052"));
        assertEquals("ULL163AL52", IndiceCatalogoConversion.formaCorta("ull163al052"));
        assertEquals("UBL29AL216", IndiceCatalogoConversion.formaCorta("UBL029.AL0216"));
        assertEquals("F67008", IndiceCatalogoConversion.sufijoDeArticulo("PXCBC-F67008"));
        assertEquals("67008", IndiceCatalogoConversion.sufijoDeArticulo("67008"));
    }

    @Test
    void sinPedidoNoHayIndiceYNoSeAvisa() {
        IndiceCatalogoConversion.Indice indice = IndiceCatalogoConversion.para(
                PedidoParaConversion.vacio(TipoPlantilla.APC, null));
        assertTrue(indice.estaVacio());
        assertTrue(indice.avisos().isEmpty());
    }

    @Test
    void unPedidoQueNoSeDejaLeerAvisaYNoPara() {
        PedidoParaConversion pedido = PedidoParaConversion.cargar(TipoPlantilla.APC,
                "no soy un excel".getBytes(), null, null);
        assertTrue(pedido.estaVacio());
        assertEquals(1, pedido.avisos().size());
        assertTrue(pedido.avisos().get(0).startsWith("No se ha podido leer el excel de pedido"));
    }
}

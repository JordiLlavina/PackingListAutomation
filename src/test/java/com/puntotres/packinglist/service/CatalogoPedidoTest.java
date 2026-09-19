package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.TipoPlantilla;

/**
 * El catálogo que se le enseña a Claude, contra los DOS excels de pedido
 * reales del repo — los únicos datos de cliente de verdad que hay.
 *
 * Lo que se ancla aquí no es el formato por el formato: es que el catálogo
 * lleve justo los campos que el operario escribe a mano y que no hay forma de
 * reparar después (color y modelo), que NO lleve cantidades —lo pedido y lo
 * empaquetado pueden diferir de verdad— y que sin excel no diga nada.
 */
class CatalogoPedidoTest {

    private static byte[] recurso(String ruta) throws IOException {
        try (InputStream entrada = CatalogoPedidoTest.class.getResourceAsStream(ruta)) {
            assertNotNull(entrada, "Falta src/test/resources" + ruta);
            return entrada.readAllBytes();
        }
    }

    private static byte[] pedidoAmi() throws IOException {
        return recurso("/ejemplos/EAN PUNTOTRES H26.xlsx");
    }

    private static byte[] pedidoApc() throws IOException {
        return recurso("/ejemplos/APC_PEDIDO_FALL26.xlsx");
    }

    /** Las líneas de datos: sin la cabecera del bloque ni la fila de columnas. */
    private static List<String> filas(String texto) {
        return Arrays.stream(texto.split("\n"))
                .filter(linea -> linea.contains(" | "))
                .skip(1) // la fila que nombra las columnas
                .toList();
    }

    // --- AMI ---

    @Test
    void elCatalogoDeAmiTraeReferenciaColorTallasYPedidos() throws IOException {
        String texto = CatalogoPedido.para(TipoPlantilla.AMI, pedidoAmi()).texto();

        // El color va con código Y nombre: el operario escribe tanto "2221"
        // como "chocolat", y el catálogo tiene que resolver las dos dudas.
        assertTrue(texto.contains("UBL029.AL0216 | 2221 CHOCOLATE BROWN"),
                "sin código y nombre de color, el catálogo no resuelve la cabecera");
        // El PO con su sufijo de destinación, que es como está en el fichero.
        assertTrue(texto.contains("07704 CH"), "el PO tiene que llevar su destinación");
    }

    /**
     * Las tallas son números escritos, y ordenarlas como texto las deja en
     * "100, 75, 80": quien lea el catálogo pensaría que el fichero está mal.
     */
    @Test
    void lasTallasDeUnCinturonSalenEnOrdenNumerico() throws IOException {
        String texto = CatalogoPedido.para(TipoPlantilla.AMI, pedidoAmi()).texto();
        assertTrue(texto.contains("75, 85, 95, 105"),
                "las tallas se ordenan por valor, no como texto");
        assertFalse(texto.contains("105, 75"), "orden alfabético de tallas");
    }

    // --- APC ---

    @Test
    void elCatalogoDeApcTraeElModeloYElColorQueEsLoQueNadieArreglaDespues()
            throws IOException {
        String texto = CatalogoPedido.para(TipoPlantilla.APC, pedidoApc()).texto();

        // Partir "sac Le Neige CLOU CAMEL" en modelo y color es, según el
        // análisis de las hojas reales, la separación más difícil de la hoja,
        // y no se puede resolver sin el catálogo de colores del cliente.
        assertTrue(texto.contains("PXCEI-F67043 | 4100128738 | Australia | le neige clou | CAB"),
                "sin modelo y color, el catálogo no ayuda donde más falta hace");
        // El pedido, entero: de la hoja solo llegan sus tres últimos dígitos.
        assertTrue(texto.contains("4100128738"), "el pedido va completo");
    }

    /**
     * Un "Document d'achat" es una destinación, un artículo y un color, con
     * una fila por talla: las tallas se juntan en una línea y no se repite el
     * artículo seis veces.
     */
    @Test
    void lasTallasDeUnMismoPedidoDeApcSeJuntanEnUnaLinea() throws IOException {
        String texto = CatalogoPedido.para(TipoPlantilla.APC, pedidoApc()).texto();
        assertTrue(texto.contains("ceinture rosette | LZZ | 75, 80, 85, 90"));
        assertEquals(70, filas(texto).size(),
                "el fichero real tiene 70 pares artículo+pedido distintos");
    }

    // --- lo que el catálogo NO lleva ---

    /**
     * Sin cantidades, a propósito: lo pedido y lo empaquetado pueden diferir
     * de verdad (una entrega parcial), y la regla 4 del prompt es explícita en
     * que ese descuadre lo tiene que ver el operario, no cuadrarlo el modelo.
     * Se comprueba por la forma de la línea —tantos campos como columnas
     * anunciadas— y no buscando un número, que podría coincidir con otro dato.
     */
    @Test
    void ningunaLineaTraeCantidades() throws IOException {
        comprobarColumnas(CatalogoPedido.para(TipoPlantilla.AMI, pedidoAmi()).texto(), 4);
        comprobarColumnas(CatalogoPedido.para(TipoPlantilla.APC, pedidoApc()).texto(), 6);
    }

    private static void comprobarColumnas(String texto, int columnas) {
        assertTrue(texto.contains("No trae cantidades"),
                "el propio catálogo tiene que decir que no trae cantidades");
        for (String fila : filas(texto)) {
            assertEquals(columnas, fila.split(" \\| ").length,
                    "línea con más campos de los anunciados: " + fila);
        }
    }

    // --- sin excel, con excel ilegible, y plantillas sin pedido ---

    @Test
    void sinExcelNoHayCatalogoYTampocoAviso() {
        CatalogoPedido.Catalogo catalogo = CatalogoPedido.para(TipoPlantilla.APC, null);
        assertTrue(catalogo.estaVacio());
        // Quien necesita el excel para otra cosa ya avisa por su cuenta:
        // repetirlo aquí solo empuja hacia abajo los avisos que hay que leer.
        assertEquals(List.of(), catalogo.avisos());
    }

    @Test
    void unExcelQueNoSeDejaLeerAvisaYNoRompeLaExtraccion() {
        CatalogoPedido.Catalogo catalogo =
                CatalogoPedido.para(TipoPlantilla.APC, "esto no es un xlsx".getBytes());
        assertTrue(catalogo.estaVacio(), "sin catálogo, pero la extracción sigue");
        assertEquals(1, catalogo.avisos().size());
        assertTrue(catalogo.avisos().get(0).contains("se han transcrito sin él"));
    }

    @Test
    void unaPlantillaGenericaNoTieneCatalogoNiLoEcha() throws IOException {
        // Estas plantillas no tienen excel de pedido: no hay nada que montar
        // ni nada de lo que avisar.
        CatalogoPedido.Catalogo catalogo =
                CatalogoPedido.para(TipoPlantilla.GENERIC, pedidoApc());
        assertTrue(catalogo.estaVacio());
        assertEquals(List.of(), catalogo.avisos());
    }
}

package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PedidoCorteTest {

    @Test
    void sumaLasLineasDeLaMismaReferenciaYColorSinMirarNadaMas() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("ULL729.AL0103", "001 BLACK", 40),
                new LineaCorte("ULL729.AL0103", "718 VANILLA CREAM", 10),
                new LineaCorte("ULL729.AL0103", "001 BLACK", 60),
                new LineaCorte("ull729.al0103", "001 BLACK", 5)), ReferenciaCorte::deAmi);

        assertEquals(1, pedido.articulos().size());
        ArticuloCorte articulo = pedido.articulos().get(0);
        assertEquals(List.of(new ColorCorte("001 BLACK", "", 105), new ColorCorte("718 VANILLA CREAM", "", 10)),
                articulo.colores());
        assertTrue(pedido.avisos().isEmpty());
    }

    @Test
    void elColorSeSumaPorSuCodigoYSeEnsenaConSuDescripcion() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("ULL729.AL0103", "001", "", 40, ""),
                new LineaCorte("ULL729.AL0103", "001", "BLACK", 60, ""),
                new LineaCorte("ULL729.AL0103", "718", "VANILLA CREAM", 10, "")), ReferenciaCorte::deAmi);

        List<ColorCorte> colores = pedido.articulos().get(0).colores();
        assertEquals(List.of(new ColorCorte("001", "BLACK", 100), new ColorCorte("718", "VANILLA CREAM", 10)),
                colores, "la descripción es la primera que traiga el pedido para ese código");
        assertEquals("001 BLACK", colores.get(0).nombre());
        assertTrue(pedido.codigosSinDescripcion().isEmpty());
    }

    @Test
    void sinDescripcionElColorEsElCodigoYLaTablaSoloRellenaHuecos() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("PXCBC-F67008", "LZZ", 5, "Sac"),
                new LineaCorte("PXCBC-F67008", "LAW", 3, "Sac"),
                new LineaCorte("PXCDS-F67043", "LZZ", 1, "Otro")), ReferenciaCorte::deApc);

        assertEquals("LZZ", pedido.articulos().get(0).colores().get(0).nombre());
        assertEquals(Set.of("LZZ", "LAW"), pedido.codigosSinDescripcion());

        PedidoCorte descrito = pedido.conDescripciones(Map.of("LZZ", "NOIR", "LAW", ""));
        assertEquals(List.of("LZZ NOIR", "LAW"), descrito.articulos().get(0).colores().stream()
                .map(ColorCorte::nombre).toList());
        assertEquals("LZZ NOIR", descrito.articulos().get(1).colores().get(0).nombre());
        assertEquals(Set.of("LAW"), descrito.codigosSinDescripcion());
        assertEquals(8, descrito.articulos().get(0).colores().stream().mapToInt(ColorCorte::bolsos).sum());
    }

    @Test
    void loQueTraeElPedidoMandaSobreLaTabla() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("ULL729.AL0103", "001", "BLACK", 1, "")), ReferenciaCorte::deAmi);

        assertEquals("001 BLACK", pedido.conDescripciones(Map.of("001", "NOIR"))
                .articulos().get(0).colores().get(0).nombre());
    }

    @Test
    void losArticulosSalenOrdenadosPorModeloYPiel() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("ULL754.AL0219", "2221", 1),
                new LineaCorte("ULL027.AL0216", "001", 1),
                new LineaCorte("ULL754.AL0137", "001", 1),
                new LineaCorte("ULL027.AL0103", "001", 1)), ReferenciaCorte::deAmi);

        assertEquals(List.of("ULL027.AL0103", "ULL027.AL0216", "ULL754.AL0137", "ULL754.AL0219"),
                pedido.articulos().stream().map(a -> a.referencia().referencia()).toList());
        assertEquals(Set.of("ULL027", "ULL754"), pedido.modelos());
    }

    @Test
    void elNombreDelBolsoEsElPrimeroQueTraigaElPedido() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("PXCBC-F67008", "LZZ", 1, ""),
                new LineaCorte("PXCBC-F67008", "LAW", 1, " Sac Le Neige "),
                new LineaCorte("PXCBC-F67008", "LAW", 1, "Otro nombre"),
                new LineaCorte("PXCDS-F67043", "LZZ", 1)), ReferenciaCorte::deApc);

        assertEquals("Sac Le Neige", pedido.articulos().get(0).nombreModelo());
        assertEquals("", pedido.articulos().get(1).nombreModelo());
    }

    @Test
    void unaReferenciaSinPielSaleIgualPeroSeAvisa() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("MUESTRA", "001", 3)), ReferenciaCorte::deAmi);

        assertEquals(1, pedido.articulos().size());
        assertTrue(pedido.avisos().get(0).contains("MUESTRA"));
    }

    @Test
    void unPedidoSinCantidadesAvisaDeQueHayQueTeclearlas() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("ULL729.AL0103", "001", 0)), ReferenciaCorte::deAmi);

        assertTrue(pedido.avisos().stream().anyMatch(aviso -> aviso.contains("cantidades")));
    }

    @Test
    void lasLineasSinReferenciaNoCuentan() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("  ", "001", 3),
                new LineaCorte(null, "001", 3)), ReferenciaCorte::deAmi);

        assertTrue(pedido.articulos().isEmpty());
    }
}

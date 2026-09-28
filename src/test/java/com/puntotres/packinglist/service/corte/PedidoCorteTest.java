package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
        assertEquals(List.of(new ColorCorte("001 BLACK", 105), new ColorCorte("718 VANILLA CREAM", 10)),
                articulo.colores());
        assertTrue(pedido.avisos().isEmpty());
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

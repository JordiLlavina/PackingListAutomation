package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.TipoMezcla;

/**
 * Cómo se llenan las cajas de una destinación.
 *
 * Dos reglas que no son obvias y que este test existe para fijar:
 * mezclar exige el mismo cartón, y solo se mezcla si ahorra una caja.
 */
class AgrupadorCajasTest {

    private static ArticuloDestinado articulo(String destino, String referencia, String color,
                                              int cantidad, String medida, int unidadesPorCaja) {
        return new ArticuloDestinado(destino, referencia, color, "U", "07001",
                cantidad, medida, unidadesPorCaja);
    }

    private static ArticuloDestinado conPedido(String destino, String referencia, String color,
                                               int cantidad, String pedido) {
        return new ArticuloDestinado(destino, referencia, color, "U", pedido,
                cantidad, "60x40x40", 10);
    }

    private static List<Integer> unidadesDe(List<CajaGenerada> cajas) {
        return cajas.stream().map(CajaGenerada::unidades).toList();
    }

    // --- Reparto dentro de un artículo ---

    @Test
    void sinMezclaCadaArticuloVaEnSusPropiasCajas() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("CHINA", "BAG-A", "NOIR", 20, "60x40x40", 10),
                        articulo("CHINA", "BAG-A", "BEIGE", 10, "60x40x40", 10)),
                TipoMezcla.NINGUNA);

        assertEquals(3, cajas.size());
        assertTrue(cajas.stream().noneMatch(CajaGenerada::esMixta));
    }

    @Test
    void elRepartoEsEquitativoYNoDejaUnaCajaCasiVacia() {
        // 31 unidades con 10 por caja: 8+8+8+7, nunca 10+10+10+1. Una caja
        // casi vacía se aplasta con el peso de las de encima.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("CHINA", "BAG-A", "NOIR", 31, "60x40x40", 10)),
                TipoMezcla.NINGUNA);

        assertEquals(List.of(8, 8, 8, 7), unidadesDe(cajas));
    }

    @Test
    void ningunaCajaPasaDeSuCapacidad() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("CHINA", "BAG-A", "NOIR", 97, "60x40x40", 10)),
                TipoMezcla.NINGUNA);

        assertTrue(cajas.stream().allMatch(c -> c.unidades() <= 10));
        assertEquals(97, cajas.stream().mapToInt(CajaGenerada::unidades).sum());
    }

    @Test
    void unaCantidadExactaLlenaLasCajasDelTodo() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("CHINA", "BAG-A", "NOIR", 20, "60x40x40", 10)),
                TipoMezcla.NINGUNA);

        assertEquals(List.of(10, 10), unidadesDe(cajas));
    }

    @Test
    void menosDeUnaCajaEsUnaCaja() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("CHINA", "BAG-A", "NOIR", 3, "60x40x40", 10)),
                TipoMezcla.NINGUNA);

        assertEquals(List.of(3), unidadesDe(cajas));
    }

    // --- Las dos reglas de mezcla ---

    @Test
    void dosReferenciasConCartonDistintoNoCompartenCajaAunqueSePuedaMezclar() {
        // La caja ES un cartón concreto: no se puede meter algo en "60x40x40"
        // y en "60x40x30" a la vez, permita o no mezclar el cliente.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "BAG-A", "NOIR", 3, "60x40x40", 10),
                        articulo("PARIS", "BAG-B", "NOIR", 2, "60x40x30", 6)),
                TipoMezcla.LIBRE);

        assertEquals(2, cajas.size());
        assertTrue(cajas.stream().noneMatch(CajaGenerada::esMixta));
    }

    @Test
    void noSeMezclaSiNoAhorraNingunaCaja() {
        // Puras: ceil(11/10) + ceil(10/10) = 3. Mezcladas: ceil(21/10) = 3.
        // Empate, y mezclar complica la etiqueta y el packing list.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "BAG-A", "NOIR", 11, "60x40x40", 10),
                        articulo("PARIS", "BAG-A", "BEIGE", 10, "60x40x40", 10)),
                TipoMezcla.LIBRE);

        assertEquals(3, cajas.size());
        assertTrue(cajas.stream().noneMatch(CajaGenerada::esMixta));
        assertEquals(List.of(6, 5, 10), unidadesDe(cajas));
    }

    @Test
    void seMezclaCuandoAhorraUnaCaja() {
        // Puras: ceil(4/10) + ceil(4/10) = 2. Mezcladas: ceil(8/10) = 1.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "BAG-A", "NOIR", 4, "60x40x40", 10),
                        articulo("PARIS", "BAG-A", "BEIGE", 4, "60x40x40", 10)),
                TipoMezcla.LIBRE);

        assertEquals(1, cajas.size());
        assertEquals(2, cajas.get(0).contenido().size());
        assertEquals(8, cajas.get(0).unidades());
    }

    @Test
    void seAgotanLosColoresDeUnaReferenciaAntesDePasarAOtra() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "BAG-B", "NOIR", 2, "60x40x40", 10),
                        articulo("PARIS", "BAG-A", "NOIR", 2, "60x40x40", 10),
                        articulo("PARIS", "BAG-A", "BEIGE", 2, "60x40x40", 10)),
                TipoMezcla.LIBRE);

        assertEquals(1, cajas.size());
        assertEquals(List.of("BAG-B", "BAG-A", "BAG-A"),
                cajas.get(0).contenido().stream().map(ContenidoCaja::referencia).toList());
    }

    @Test
    void conMezclaDeMismoPedidoNoSeJuntanPedidosDistintos() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(conPedido("PARIS", "BAG-A", "NOIR", 2, "07001"),
                        conPedido("PARIS", "BAG-A", "BEIGE", 2, "07002")),
                TipoMezcla.MISMO_PEDIDO);

        assertEquals(2, cajas.size());
        assertTrue(cajas.stream().noneMatch(CajaGenerada::esMixta));
    }

    @Test
    void conMezclaDeMismoPedidoSiSeJuntaElMismoPedido() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(conPedido("PARIS", "BAG-A", "NOIR", 2, "07001"),
                        conPedido("PARIS", "BAG-A", "BEIGE", 2, "07001")),
                TipoMezcla.MISMO_PEDIDO);

        assertEquals(1, cajas.size());
        assertTrue(cajas.get(0).esMixta());
    }

    @Test
    void enUnaCajaMezcladaCadaUnidadOcupaSegunSuCapacidad() {
        // BAG-A: 10/caja. BAG-C: 5/caja. 5 de A (media caja) más 2 de C (dos
        // quintos) caben juntas; contarlas por unidades sueltas no tendría
        // sentido, porque no ocupan lo mismo.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "BAG-A", "NOIR", 5, "60x40x40", 10),
                        articulo("PARIS", "BAG-C", "NOIR", 2, "60x40x40", 5)),
                TipoMezcla.LIBRE);

        assertEquals(1, cajas.size());
        assertEquals(7, cajas.get(0).unidades());
    }

    @Test
    void unaCajaMezcladaNoSePasaDeLlena() {
        // 8 de A (0,8 de caja) y 4 de C (0,8 de caja): 1,6 -> dos cajas, y
        // ninguna puede pasar de su ocupación.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "BAG-A", "NOIR", 8, "60x40x40", 10),
                        articulo("PARIS", "BAG-C", "NOIR", 4, "60x40x40", 5)),
                TipoMezcla.LIBRE);

        assertEquals(2, cajas.size());
        assertEquals(12, cajas.stream().mapToInt(CajaGenerada::unidades).sum());
        assertTrue(cajas.stream().allMatch(AgrupadorCajasTest::cabeDeVerdad));
    }

    /** La suma de fracciones ocupadas de una caja no puede pasar de 1. */
    private static boolean cabeDeVerdad(CajaGenerada caja) {
        double ocupacion = caja.contenido().stream()
                .mapToDouble(c -> c.unidades() / (double) capacidadDe(c.referencia()))
                .sum();
        return ocupacion <= 1.0001;
    }

    private static int capacidadDe(String referencia) {
        return "BAG-C".equals(referencia) ? 5 : 10;
    }

    // --- Otras destinaciones ---

    @Test
    void lasDestinacionesNoSeMezclanEntreSi() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("CHINA", "BAG-A", "NOIR", 2, "60x40x40", 10),
                        articulo("PARIS", "BAG-A", "NOIR", 2, "60x40x40", 10)),
                TipoMezcla.LIBRE);

        assertEquals(2, cajas.size());
        assertEquals(List.of("CHINA", "PARIS"),
                cajas.stream().map(CajaGenerada::destino).toList());
    }

    @Test
    void laAlturaDeLaCajaEsLaUltimaDimension() {
        CajaGenerada caja = new AgrupadorCajas().agrupar(
                List.of(articulo("CHINA", "BAG-A", "NOIR", 1, "60x40x45", 10)),
                TipoMezcla.NINGUNA).get(0);

        assertEquals(45, caja.alturaCm());
    }

    @Test
    void lasTallasDeUnCinturonSonArticulosDistintos() {
        // Aunque compartan referencia y color: cada talla es un SKU con su
        // propio código de barras.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(new ArticuloDestinado("CHINA", "UBL1", "2221", "75", "07001",
                                4, "60x40x40", 10),
                        new ArticuloDestinado("CHINA", "UBL1", "2221", "85", "07001",
                                4, "60x40x40", 10)),
                TipoMezcla.NINGUNA);

        assertEquals(2, cajas.size());
    }
}

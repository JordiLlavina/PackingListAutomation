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

    /** Un cinturón se reconoce por el prefijo UBL de la referencia, y lleva talla. */
    private static ArticuloDestinado cinturon(String destino, String referencia, String color,
                                              String talla, int cantidad) {
        return new ArticuloDestinado(destino, referencia, color, talla, "07001",
                cantidad, "60x40x40", 10);
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

    // --- Tipo de artículo y tallas ---

    @Test
    void lasTallasDeUnCinturonCompartenCajaSiAsiSeAhorraUnBulto() {
        // Cada talla es un artículo con su propio código de barras, pero eso
        // no obliga a darle un cartón propio: cuatro y cuatro de a diez por
        // caja caben juntas, y separarlas dejaría dos cajas a medio llenar.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(cinturon("CHINA", "UBL1", "2221", "75", 4),
                        cinturon("CHINA", "UBL1", "2221", "85", 4)),
                TipoMezcla.NINGUNA);

        assertEquals(1, cajas.size());
        assertEquals(8, cajas.get(0).unidades());
        assertEquals(List.of("75", "85"),
                cajas.get(0).contenido().stream().map(ContenidoCaja::talla).toList());
    }

    @Test
    void unaTallaQueNoCabeSigueYendoASuPropiaCaja() {
        // La mezcla no puede pasarse de la capacidad del cartón: 8 + 6 de a
        // diez por caja son dos bultos, mezclados pero llenos.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(cinturon("CHINA", "UBL1", "2221", "75", 8),
                        cinturon("CHINA", "UBL1", "2221", "85", 6)),
                TipoMezcla.NINGUNA);

        assertEquals(2, cajas.size());
        assertEquals(14, cajas.stream().mapToInt(CajaGenerada::unidades).sum());
        assertTrue(cajas.stream().allMatch(caja -> caja.unidades() <= 10));
    }

    @Test
    void unBolsoYUnCinturonNuncaCompartenCajaAunqueQuepan() {
        // No es cuestión de hueco: son artículos de naturaleza distinta y el
        // almacén los prepara por separado. Manda sobre LIBRE.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "ULL164", "NOIR", 4, "60x40x40", 10),
                        cinturon("PARIS", "UBL029", "2221", "75", 4)),
                TipoMezcla.LIBRE);

        assertEquals(2, cajas.size());
        assertTrue(cajas.stream().noneMatch(CajaGenerada::esMixta));
    }

    @Test
    void doceCinturonesDeTresTallasCabenEnDosCajasYNoEnTres() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(cinturon("CHINA", "UBL1", "2221", "75", 5),
                        cinturon("CHINA", "UBL1", "2221", "85", 4),
                        cinturon("CHINA", "UBL1", "2221", "95", 3)),
                TipoMezcla.NINGUNA);

        assertEquals(2, cajas.size());
        assertEquals(12, cajas.stream().mapToInt(CajaGenerada::unidades).sum());
    }

    // --- Que la mezcla no salga cara ---

    @Test
    void siElLlenadoRealNoAhorraUnBultoSeVuelveACajasPuras() {
        // El recuento de la mezcla es una COTA (aquí 6 contra 7 puras), y con
        // artículos que no ocupan lo mismo el llenado real no siempre la
        // alcanza. Cuando no la alcanza, mezclar no ahorra nada y solo deja
        // bultos mixtos: se vuelve atrás.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "BAG-A", "NOIR", 3, "60x40x40", 2),
                        articulo("PARIS", "BAG-B", "NOIR", 3, "60x40x40", 3),
                        articulo("PARIS", "BAG-C", "NOIR", 5, "60x40x40", 3),
                        articulo("PARIS", "BAG-D", "NOIR", 8, "60x40x40", 10),
                        articulo("PARIS", "BAG-E", "NOIR", 2, "60x40x40", 2)),
                TipoMezcla.LIBRE);

        assertEquals(7, cajas.size());
        assertTrue(cajas.stream().noneMatch(CajaGenerada::esMixta),
                "si no ahorra un bulto, mejor puras: la etiqueta y el packing list son más simples");
    }

    @Test
    void elOrdenEnQueLleganLosArticulosNoCambiaElNumeroDeCajas() {
        // Se empaqueta primero lo que peor encaja. Sin ese criterio, las
        // unidades menudas llenan las cajas, la grande ya no cabe en ninguna
        // y hace falta un cartón más solo por el orden de las filas.
        List<ArticuloDestinado> grande = articulos(2, 3, 1, 2, 2, 3);
        List<ArticuloDestinado> alReves = articulos(2, 3, 2, 3, 1, 2);

        assertEquals(2, new AgrupadorCajas().agrupar(grande, TipoMezcla.LIBRE).size());
        assertEquals(2, new AgrupadorCajas().agrupar(alReves, TipoMezcla.LIBRE).size());
    }

    @Test
    void elSobranteDeUnaMezclaNoSeQuedaEnUnaCajaCasiVacia() {
        // Cuatro cajas hacen falta; lo que no vale es tres llenas y una al
        // diez por ciento, que es lo que sale de llenar a tope desde el
        // principio. Una caja casi vacía se aplasta con el peso de las de
        // encima, igual que en el reparto de un artículo solo.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                articulos(4, 6, 5, 8, 3, 8, 6, 10, 7, 10), TipoMezcla.LIBRE);

        assertEquals(4, cajas.size());
        assertTrue(cajas.stream().allMatch(caja -> caja.unidades() >= 5),
                "ninguna caja se queda con las sobras de las demás");
    }

    /** Artículos de un mismo destino y cartón, en pares cantidad/capacidad. */
    private static List<ArticuloDestinado> articulos(int... cantidadYCapacidad) {
        List<ArticuloDestinado> articulos = new java.util.ArrayList<>();
        for (int i = 0; i < cantidadYCapacidad.length; i += 2) {
            articulos.add(articulo("PARIS", "BAG-" + (i / 2), "NOIR",
                    cantidadYCapacidad[i], "60x40x40", cantidadYCapacidad[i + 1]));
        }
        return articulos;
    }
}

package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * El palet tiene cuatro posiciones (2x2) y cada una es una pila
 * independiente. Lo único que limita es la altura: el peso se revisa después,
 * en la pantalla de revisión.
 */
class ApiladorPaletsTest {

    private static final int POSICIONES = 4;

    private static CajaGenerada caja(int alturaCm) {
        return new CajaGenerada("CHINA", "60x40x" + alturaCm,
                List.of(new ContenidoCaja("BAG-A", "NOIR", "U", "07001", 10)));
    }

    @Test
    void cuatroCajasQueCabenVanEnUnPaletUnaPorPila() {
        List<CajaGenerada> cajas = List.of(caja(40), caja(40), caja(40), caja(30));

        ResultadoApilado apilado = new ApiladorPalets().apilar(cajas, 147, POSICIONES);

        assertEquals(1, apilado.getPalets().size());
        assertEquals(4, apilado.getPalets().get(0).pilas().size());
        assertEquals(40, apilado.getPalets().get(0).alturaMaximaCm());
    }

    @Test
    void laQuintaCajaSeApilaEncimaDeLaPrimera() {
        List<CajaGenerada> cajas = List.of(caja(40), caja(40), caja(40), caja(40), caja(40));

        PaletGenerado palet = new ApiladorPalets().apilar(cajas, 147, POSICIONES)
                .getPalets().get(0);

        assertEquals(5, palet.cajas().size());
        assertEquals(80, palet.alturaMaximaCm());
    }

    @Test
    void cuandoNoCabeEnNingunaPilaSeAbrePaletNuevo() {
        // Altura útil 100: dos cajas de 40 por pila, ocho en total.
        List<CajaGenerada> cajas = new ArrayList<>(Collections.nCopies(9, caja(40)));

        ResultadoApilado apilado = new ApiladorPalets().apilar(cajas, 100, POSICIONES);

        assertEquals(2, apilado.getPalets().size());
        assertEquals(8, apilado.getPalets().get(0).cajas().size());
        assertEquals(1, apilado.getPalets().get(1).cajas().size());
    }

    @Test
    void ningunaPilaSePasaDeLaAlturaUtil() {
        List<CajaGenerada> cajas = new ArrayList<>(Collections.nCopies(20, caja(45)));

        ResultadoApilado apilado = new ApiladorPalets().apilar(cajas, 147, POSICIONES);

        for (PaletGenerado palet : apilado.getPalets()) {
            for (List<CajaGenerada> pila : palet.pilas()) {
                assertTrue(pila.stream().mapToInt(CajaGenerada::alturaCm).sum() <= 147);
            }
        }
    }

    @Test
    void nadieSeQuedaFuera() {
        List<CajaGenerada> cajas = new ArrayList<>(Collections.nCopies(23, caja(45)));

        ResultadoApilado apilado = new ApiladorPalets().apilar(cajas, 147, POSICIONES);

        assertEquals(23, apilado.getPalets().stream()
                .mapToInt(palet -> palet.cajas().size()).sum());
    }

    @Test
    void lasCajasSeColocanDeMayorAMenorAltura() {
        // Empezar por las altas es lo que evita que una caja alta se quede sin
        // sitio cuando las bajas ya han llenado las cuatro pilas.
        List<CajaGenerada> cajas = List.of(caja(20), caja(50), caja(30));

        PaletGenerado palet = new ApiladorPalets().apilar(cajas, 147, POSICIONES)
                .getPalets().get(0);

        assertEquals(List.of(50, 30, 20),
                palet.cajas().stream().map(CajaGenerada::alturaCm).toList());
    }

    @Test
    void unaCajaMasAltaQueElPaletBloquea() {
        ResultadoApilado apilado = new ApiladorPalets().apilar(List.of(caja(200)), 147, POSICIONES);

        assertTrue(apilado.getBloqueos().stream().anyMatch(b -> b.contains("200")));
        assertTrue(apilado.getPalets().isEmpty());
    }

    @Test
    void unaMedidaIlegibleBloqueaEnVezDeApilarAOjo() {
        CajaGenerada sinMedida = new CajaGenerada("CHINA", "60x40",
                List.of(new ContenidoCaja("BAG-A", "NOIR", "U", "07001", 10)));

        ResultadoApilado apilado = new ApiladorPalets().apilar(List.of(sinMedida), 147, POSICIONES);

        assertTrue(apilado.getBloqueos().stream().anyMatch(b -> b.contains("60x40")));
    }

    @Test
    void seAceptaElUltimoPaletAMediaAltura() {
        ResultadoApilado apilado = new ApiladorPalets().apilar(List.of(caja(40)), 147, POSICIONES);

        assertEquals(1, apilado.getPalets().size());
        assertTrue(apilado.getBloqueos().isEmpty());
    }

    @Test
    void sinCajasNoHayPalets() {
        ResultadoApilado apilado = new ApiladorPalets().apilar(List.of(), 147, POSICIONES);

        assertTrue(apilado.getPalets().isEmpty());
        assertTrue(apilado.getBloqueos().isEmpty());
    }
}

package com.puntotres.packinglist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * El volumen de los packing lists va como FÓRMULA de Excel con las medidas a
 * la vista, para que se vea de dónde sale y se pueda corregir a mano.
 */
class VolumenUtilTest {

    @Test
    void unTerminoPorMedidaConCuantasCajasLaLlevanYSusLadosEnMetros() {
        assertEquals("2*0.6*0.4*0.3+51*0.6*0.4*0.4", VolumenUtil.formulaVolumenM3(
                concat(List.of("60x40x30", "60X40X30"), repetir("60x40x40", 51))));
    }

    @Test
    void unaSolaCajaDeUnaMedidaVaSinElUno() {
        assertEquals("0.6*0.4*0.4", VolumenUtil.formulaVolumenM3(List.of("60x40x40")));
    }

    @Test
    void lasMedidasQueFaltanNoSumanYSinNingunaNoHayFormula() {
        assertEquals("0.6*0.4*0.4", VolumenUtil.formulaVolumenM3(Arrays.asList("60x40x40", null, " ")));
        assertNull(VolumenUtil.formulaVolumenM3(Arrays.asList(null, "")));
    }

    @Test
    void unaMedidaIlegibleLanzaComoLaSuma() {
        assertThrows(IllegalArgumentException.class,
                () -> VolumenUtil.formulaVolumenM3(List.of("60x40")));
    }

    @Test
    void laFormulaYLaSumaDanLoMismo() {
        List<String> medidas = List.of("60x40x30", "60x40x40", "60x40x40", "40.5x30x20");
        assertEquals(VolumenUtil.volumenTotalM3(medidas),
                2 * 0.6 * 0.4 * 0.4 + 0.6 * 0.4 * 0.3 + 0.405 * 0.3 * 0.2, 1e-9);
        assertEquals("0.6*0.4*0.3+2*0.6*0.4*0.4+0.405*0.3*0.2",
                VolumenUtil.formulaVolumenM3(medidas));
    }

    private static List<String> repetir(String valor, int veces) {
        return java.util.Collections.nCopies(veces, valor);
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> todo = new java.util.ArrayList<>(a);
        todo.addAll(b);
        return todo;
    }
}

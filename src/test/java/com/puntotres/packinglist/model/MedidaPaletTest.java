package com.puntotres.packinglist.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * La medida de un palet cargado: base 80x120 siempre, y de alto la suma de
 * sus niveles de cajas más lo que mide el palet vacío.
 */
class MedidaPaletTest {

    private static final int PALET_VACIO_CM = 11;

    private static List<String> cajas(int cuantas, String medida) {
        return new ArrayList<>(Collections.nCopies(cuantas, medida));
    }

    private static List<String> juntar(List<String> a, List<String> b) {
        List<String> todas = new ArrayList<>(a);
        todas.addAll(b);
        return todas;
    }

    /** El ejemplo del usuario: dos niveles de 20 y uno de 40 son 80 cm de carga. */
    @Test
    void ochoCajasBajasYCuatroAltasSonTresNiveles() {
        List<String> medidas = juntar(cajas(8, "60x40x20"), cajas(4, "60x40x40"));

        assertEquals("80x120x91", MedidaPalet.de(null, medidas, PALET_VACIO_CM));
    }

    /** La regla de las 12 cajas no existe: cuentan las cajas que lleva de verdad. */
    @Test
    void veinteCajasDeTreintaSonCincoNiveles() {
        assertEquals("80x120x161", MedidaPalet.de(null, cajas(20, "60x40x30"), PALET_VACIO_CM));
    }

    /** Un 70x60 ocupa medio nivel de un palet de 80x120: caben dos, no cuatro. */
    @Test
    void lasCajasDeSetentaPorSesentaVanDeDosEnDos() {
        assertEquals("80x120x51", MedidaPalet.de(null, cajas(4, "70x60x20"), PALET_VACIO_CM));
    }

    @Test
    void cuantasCajasCabenEnUnNivel() {
        assertEquals(4, MedidaPalet.cajasPorNivel(new MedidaCaja(60, 40, 40)));
        assertEquals(4, MedidaPalet.cajasPorNivel(new MedidaCaja(40, 60, 40)));
        assertEquals(2, MedidaPalet.cajasPorNivel(new MedidaCaja(70, 60, 20)));
        assertEquals(2, MedidaPalet.cajasPorNivel(new MedidaCaja(60, 60, 40)));
        // Una caja que no cabe en la base ocupa el nivel entero: nunca cero.
        assertEquals(1, MedidaPalet.cajasPorNivel(new MedidaCaja(130, 90, 40)));
    }

    /** Un nivel con cajas de dos alturas mide lo que la más alta. */
    @Test
    void unNivelMezcladoMideLoQueSuCajaMasAlta() {
        List<String> medidas = Arrays.asList("60x40x20", "60x40x40", "60x40x20", "60x40x40");

        assertEquals("80x120x51", MedidaPalet.de(null, medidas, PALET_VACIO_CM));
    }

    /** El último nivel a medias cuenta entero: la caja que va arriba sube igual. */
    @Test
    void unNivelIncompletoCuentaEntero() {
        assertEquals("80x120x71", MedidaPalet.de(null, cajas(5, "60x40x30"), PALET_VACIO_CM));
    }

    /** Un 70x60 y dos 60x40 llenan un nivel entre los tres. */
    @Test
    void cajasDeDistintaBaseCompartenNivel() {
        List<String> medidas = Arrays.asList("60x40x20", "70x60x40", "60x40x20");

        assertEquals("80x120x51", MedidaPalet.de(null, medidas, PALET_VACIO_CM));
    }

    /** Sin la medida de una caja no se sabe la altura: se rotula, no se inventa. */
    @Test
    void unaCajaSinMedidaDejaLaAlturaEnInterrogante() {
        List<String> medidas = juntar(cajas(3, "60x40x40"), Arrays.asList((String) null));

        assertEquals("80x120x?", MedidaPalet.de(null, medidas, PALET_VACIO_CM));
        assertEquals("80x120x?", MedidaPalet.de("  ", Arrays.asList("60x40"), PALET_VACIO_CM));
    }

    /** Lo medido en el almacén manda sobre lo calculado. */
    @Test
    void laMedidaEscritaManda() {
        assertEquals("80x120x170", MedidaPalet.de("80x120x170", cajas(4, "60x40x40"), PALET_VACIO_CM));
    }

    @Test
    void soloUnaMedidaConSusTresLadosSumaVolumen() {
        assertTrue(MedidaPalet.tieneVolumen("80x120x91"));
        assertFalse(MedidaPalet.tieneVolumen("80x120x?"));
        assertFalse(MedidaPalet.tieneVolumen(null));
    }
}

package com.puntotres.packinglist.shipping.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class CustomsCheckerTest {

    private final CustomsChecker checker = new CustomsChecker(List.of("ES", "FR", "IT", "DE"));

    @Test
    void entreDosPaisesDeLaUnionNoHayAduana() {
        assertFalse(checker.esAduanero("ES", "FR"));
        assertFalse(checker.esAduanero("ES", "ES"));
    }

    @Test
    void conUnPaisFueraDeLaUnionSiHay() {
        assertTrue(checker.esAduanero("ES", "MA"));
        assertTrue(checker.esAduanero("MA", "FR"));
        assertTrue(checker.esAduanero("MA", "MA"), "dos países fuera también declaran: solo la unión aduanera del yml se libra");
    }

    @Test
    void sinPaisSeAsumeAduaneroParaNoOcultarLaDeclaracion() {
        assertTrue(checker.esAduanero(null, "MA"));
    }
}

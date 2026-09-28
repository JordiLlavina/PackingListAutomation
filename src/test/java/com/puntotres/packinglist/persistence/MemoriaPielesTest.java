package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Lo que el programa recuerda de las pieles entre temporadas. Cada test usa
 * sus propias referencias: la base de datos en memoria es la misma para toda
 * la suite.
 */
@SpringBootTest
class MemoriaPielesTest {

    @Autowired
    private MemoriaPieles memoria;

    @Test
    void elNombreDeLaPielValeParaTodosLosBolsosQueLaUsan() {
        memoria.recordar("AMI", "AL0901", "ULL729.AL0901", "Box calf", "", List.of());

        MemoriaPieles.Recordadas otroBolso = memoria.buscar("AMI", "AL0901", "ULL712.AL0901");
        assertEquals("Box calf", otroBolso.nombrePiel());
        assertTrue(otroBolso.combinaciones().isEmpty(), "las combinaciones son del bolso, no de la piel");
        assertEquals("", otroBolso.forro());
    }

    @Test
    void elForroYLasCombinacionesSonDelBolso() {
        memoria.recordar("AMI", "AL0902", "ULL027.AL0902", "Vachette",
                "Cabretilla", Arrays.asList("Ante", "", null, "Charol"));

        MemoriaPieles.Recordadas recordadas = memoria.buscar("AMI", "AL0902", "ULL027.AL0902");
        assertEquals("Cabretilla", recordadas.forro());
        assertEquals(List.of("Ante", "Charol"), recordadas.combinaciones());
    }

    @Test
    void sinNombreDePielNoSeGuardaNiSeBorraNada() {
        memoria.recordar("AMI", "AL0903", "ULL027.AL0903", "Nappa", "Tela", List.of("Ante"));
        memoria.recordar("AMI", "AL0903", "ULL027.AL0903", "  ", "", List.of());

        MemoriaPieles.Recordadas recordadas = memoria.buscar("AMI", "AL0903", "ULL027.AL0903");
        assertEquals("Nappa", recordadas.nombrePiel());
        assertEquals("Tela", recordadas.forro());
        assertEquals(List.of("Ante"), recordadas.combinaciones());
    }

    @Test
    void quitarUnaCombinacionConNombreSiSeRecuerda() {
        memoria.recordar("AMI", "AL0904", "ULL027.AL0904", "Nappa", "Tela", List.of("Ante"));
        memoria.recordar("AMI", "AL0904", "ULL027.AL0904", "Nappa", "", List.of());

        MemoriaPieles.Recordadas recordadas = memoria.buscar("AMI", "AL0904", "ULL027.AL0904");
        assertTrue(recordadas.combinaciones().isEmpty());
        assertEquals("", recordadas.forro());
    }

    @Test
    void lasClavesNoDistinguenMayusculasPeroElNombreSeGuardaComoSeTeclea() {
        memoria.recordar("ami", " al0905 ", "ull729.al0905", "Vachette Grainée", "", List.of());

        assertEquals("Vachette Grainée", memoria.buscar("AMI", "AL0905", "ULL729.AL0905").nombrePiel());
    }

    @Test
    void loQueNoSeConoceVuelveVacioYNoNull() {
        MemoriaPieles.Recordadas nada = memoria.buscar("APC", "PXZZZ", "PXZZZ-F00000");

        assertEquals("", nada.nombrePiel());
        assertEquals("", nada.forro());
        assertTrue(nada.combinaciones().isEmpty());
    }

    @Test
    void unaReferenciaSinPielGuardaSoloElBolso() {
        memoria.recordar("AMI", "", "MUESTRA9", "Nappa", "Tela", List.of("Ante"));

        MemoriaPieles.Recordadas recordadas = memoria.buscar("AMI", "", "MUESTRA9");
        assertEquals("", recordadas.nombrePiel(), "sin ref de piel no hay dónde guardar su nombre");
        assertEquals(List.of("Ante"), recordadas.combinaciones());
    }
}

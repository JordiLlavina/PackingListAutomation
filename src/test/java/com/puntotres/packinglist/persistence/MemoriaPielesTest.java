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

    @Test
    void losColoresDePielSonDeCadaColorDelBolso() {
        memoria.recordarColores("AMI", "ULL027.AL0906", "001", "Box calf", "BLACK", "NOIR",
                List.of("NEGRO", "", "MARRON"));
        memoria.recordarColores("AMI", "ULL027.AL0906", "718", "Box calf", "CRUDO", "", List.of());

        MemoriaPieles.ColoresRecordados negro = memoria.buscarColores("ami", "ull027.al0906", "001");
        assertEquals("BLACK", negro.colorPiel());
        assertEquals("NOIR", negro.colorForro());
        assertEquals(List.of("NEGRO", "", "MARRON"), negro.coloresCombinacion(),
                "los huecos se quedan: van emparejados por posición con las combinaciones");
        assertEquals("CRUDO", memoria.buscarColores("AMI", "ULL027.AL0906", "718").colorPiel());
        assertEquals(MemoriaPieles.ColoresRecordados.NINGUNO,
                memoria.buscarColores("AMI", "ULL027.AL0906", "A237"));
    }

    @Test
    void laTablaDePielesAnadeLasNuevasVaciasYNoTocaLasQueYaEstaban() {
        memoria.guardarPiel("PRUEBAP", "pz1", "Box calf");

        Incorporacion incorporacion = memoria.incorporarPieles("PRUEBAP", List.of("PZ1", "PZ2", "pz2", " "));

        assertEquals(List.of("PZ2"), incorporacion.anadidos());
        assertEquals(List.of("PZ1"), incorporacion.yaEstaban());
        assertEquals(List.of("PZ1", "PZ2"), memoria.pielesDe("PRUEBAP").stream().map(MemoriaPiel::getRefPiel).toList());
        assertEquals("Box calf", memoria.buscar("PRUEBAP", "PZ1", "X.PZ1").nombrePiel());
        assertEquals("", memoria.buscar("PRUEBAP", "PZ2", "X.PZ2").nombrePiel());
    }

    @Test
    void desdeSuTablaUnNombreSePuedeVaciarYUnaPielBorrar() {
        memoria.guardarPiel("PRUEBAP", "PZ3", "Nappa");
        memoria.guardarPiel("PRUEBAP", "PZ3", "");

        assertEquals("", memoria.buscar("PRUEBAP", "PZ3", "X.PZ3").nombrePiel(),
                "en su tabla vaciar es corregir, al revés que al generar el corte");
        assertTrue(memoria.borrarPiel("PRUEBAP", "pz3"));
        assertTrue(!memoria.borrarPiel("PRUEBAP", "PZ3"));
    }

    @Test
    void sinNombreDePielNoSeGuardanLosColores() {
        memoria.recordarColores("AMI", "ULL027.AL0907", "001", "Box calf", "BLACK", "", List.of());
        memoria.recordarColores("AMI", "ULL027.AL0907", "001", " ", "OTRO", "", List.of());

        assertEquals("BLACK", memoria.buscarColores("AMI", "ULL027.AL0907", "001").colorPiel());
    }
}

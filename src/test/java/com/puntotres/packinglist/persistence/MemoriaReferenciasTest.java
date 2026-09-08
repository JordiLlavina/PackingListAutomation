package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Lo que el programa aprende de cada referencia entre envíos: qué cartón usa
 * y cuántas unidades le caben. Sin esto habría que teclearlo entero en cada
 * entrega del taller.
 */
@SpringBootTest
class MemoriaReferenciasTest {

    @Autowired
    private MemoriaReferencias memoria;

    @Test
    void loRecordadoSeEncuentraPorClienteYReferencia() {
        memoria.recordar("AMI", "ULL164.AL0052", "60x40x40", 8);

        MemoriaReferencias.DatosCaja datos = memoria.buscar("AMI", "ULL164.AL0052").orElseThrow();
        assertEquals("60x40x40", datos.medidaCaja());
        assertEquals(8, datos.unidadesPorCaja());
    }

    @Test
    void laUltimaEjecucionGana() {
        memoria.recordar("AMI", "ULL700.AL0052", "60x40x40", 6);
        memoria.recordar("AMI", "ULL700.AL0052", "60x40x30", 4);

        MemoriaReferencias.DatosCaja datos = memoria.buscar("AMI", "ULL700.AL0052").orElseThrow();
        assertEquals("60x40x30", datos.medidaCaja());
        assertEquals(4, datos.unidadesPorCaja());
    }

    @Test
    void elPesoNetoSeGuardaYSeRecupera() {
        memoria.recordar("AMI", "CON-PESO", "60x40x40", 8, 11.4);

        assertEquals(11.4, memoria.buscar("AMI", "CON-PESO").orElseThrow().pesoNetoKg());
    }

    @Test
    void unaReferenciaSinPesarNoTienePeso() {
        memoria.recordar("AMI", "SIN-PESO", "60x40x40", 8);

        assertNull(memoria.buscar("AMI", "SIN-PESO").orElseThrow().pesoNetoKg());
    }

    @Test
    void guardarSinPesoNoBorraElPesoQueYaHabia() {
        // Pesar una caja cuesta bajarla a la báscula. Perder ese dato porque
        // la entrega siguiente se generó con la casilla vacía sería tirar el
        // trabajo de alguien; el cartón y las unidades sí se sustituyen.
        memoria.recordar("AMI", "PESADA-UNA-VEZ", "60x40x40", 8, 11.4);

        memoria.recordar("AMI", "PESADA-UNA-VEZ", "60x40x30", 6);

        MemoriaReferencias.DatosCaja datos =
                memoria.buscar("AMI", "PESADA-UNA-VEZ").orElseThrow();
        assertEquals(11.4, datos.pesoNetoKg(), "el peso sobrevive");
        assertEquals("60x40x30", datos.medidaCaja(), "el cartón sí se sustituye");
    }

    @Test
    void unPesoQueNoSeaPositivoSeIgnoraComoUnCampoVacio() {
        memoria.recordar("AMI", "PESO-CERO", "60x40x40", 8, 0.0);

        assertNull(memoria.buscar("AMI", "PESO-CERO").orElseThrow().pesoNetoKg());
    }

    @Test
    void elClienteFormaParteDeLaClave() {
        memoria.recordar("AMI", "REPE", "60x40x40", 8);

        assertTrue(memoria.buscar("APC", "REPE").isEmpty());
    }

    @Test
    void elColorNoFormaParteDeLaClave() {
        // Todos los colores de una referencia comparten cartón y unidades por
        // caja: memorizar por color multiplicaría las filas sin ganar nada.
        memoria.recordar("AMI", "ULL999.AL0001", "60x40x40", 8);

        assertEquals(8, memoria.buscar("AMI", "ULL999.AL0001").orElseThrow().unidadesPorCaja());
    }

    @Test
    void noSeMemorizaUnValorSinRellenar() {
        memoria.recordar("AMI", "SIN-UDS", "60x40x40", null);

        assertTrue(memoria.buscar("AMI", "SIN-UDS").isEmpty(),
                "memorizar el valor por defecto lo daría por bueno la próxima vez");
    }

    @Test
    void noSeMemorizaUnaCantidadImposible() {
        memoria.recordar("AMI", "CERO-UDS", "60x40x40", 0);

        assertTrue(memoria.buscar("AMI", "CERO-UDS").isEmpty());
    }

    @Test
    void laClaveNoDistingueMayusculasNiEspacios() {
        memoria.recordar("AMI", "ull800.al0052", "60x40x40", 8);

        assertEquals(8, memoria.buscar("ami", " ULL800.AL0052 ").orElseThrow().unidadesPorCaja());
    }

    @Test
    void unaReferenciaQueNoSeHaVistoNuncaNoSeInventa() {
        assertTrue(memoria.buscar("AMI", "JAMAS-VISTA").isEmpty());
    }
}

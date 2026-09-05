package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

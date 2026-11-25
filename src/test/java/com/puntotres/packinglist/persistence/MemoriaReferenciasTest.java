package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.service.catalogo.CatalogoModelos;

/**
 * Lo que el programa aprende de cada referencia entre envíos: qué cartón usa,
 * cuántas unidades le caben y lo que pesa una unidad. Sin esto habría que
 * teclearlo entero en cada entrega. Solo se aprende de los artículos que ya
 * están en el catálogo: el packing no da de alta modelos.
 */
@SpringBootTest
class MemoriaReferenciasTest {

    @Autowired
    private MemoriaReferencias memoria;

    @Autowired
    private CatalogoModelos catalogo;

    @Test
    void loRecordadoSeEncuentraPorClienteYReferencia() {
        catalogo.asegurarArticulo("AMI", "ULL164.AL0052");
        memoria.recordarCaja("AMI", "ULL164.AL0052", "60x40x40", 8);

        MemoriaReferencias.DatosCaja datos = memoria.buscar("AMI", "ULL164.AL0052").orElseThrow();
        assertEquals("60x40x40", datos.medidaCaja());
        assertEquals(8, datos.unidadesPorCaja());
    }

    @Test
    void laUltimaEjecucionGana() {
        catalogo.asegurarArticulo("AMI", "ULL700.AL0052");
        memoria.recordarCaja("AMI", "ULL700.AL0052", "60x40x40", 6);
        memoria.recordarCaja("AMI", "ULL700.AL0052", "60x40x30", 4);

        MemoriaReferencias.DatosCaja datos = memoria.buscar("AMI", "ULL700.AL0052").orElseThrow();
        assertEquals("60x40x30", datos.medidaCaja());
        assertEquals(4, datos.unidadesPorCaja());
    }

    @Test
    void elClienteFormaParteDeLaClave() {
        catalogo.asegurarArticulo("AMI", "ULL701.AL0052");
        memoria.recordarCaja("AMI", "ULL701.AL0052", "60x40x40", 8);

        assertTrue(memoria.buscar("APC", "ULL701.AL0052").isEmpty());
    }

    @Test
    void noSeMemorizaUnValorSinRellenar() {
        catalogo.asegurarArticulo("AMI", "ULL702.AL0052");
        memoria.recordarCaja("AMI", "ULL702.AL0052", "60x40x40", null);

        assertTrue(memoria.buscar("AMI", "ULL702.AL0052").isEmpty(),
                "memorizar el valor por defecto lo daría por bueno la próxima vez");
    }

    @Test
    void noSeMemorizaUnaCantidadImposible() {
        catalogo.asegurarArticulo("AMI", "ULL703.AL0052");
        memoria.recordarCaja("AMI", "ULL703.AL0052", "60x40x40", 0);

        assertTrue(memoria.buscar("AMI", "ULL703.AL0052").isEmpty());
    }

    @Test
    void laClaveNoDistingueMayusculasNiEspacios() {
        catalogo.asegurarArticulo("AMI", "ULL800.AL0052");
        memoria.recordarCaja("AMI", "ull800.al0052", "60x40x40", 8);

        assertEquals(8, memoria.buscar("ami", " ULL800.AL0052 ").orElseThrow().unidadesPorCaja());
    }

    @Test
    void unaReferenciaQueNoSeHaVistoNuncaNoSeInventa() {
        assertTrue(memoria.buscar("AMI", "JAMAS-VISTA").isEmpty());
        assertTrue(memoria.pesoNetoUnidad("AMI", "JAMAS-VISTA").isEmpty());
    }

    @Test
    void unArticuloSinCartonNoSaleEnBuscar() {
        // Dado de alta desde el catálogo pero sin decidir cómo se empaqueta:
        // para el taller es una referencia que no se ha visto nunca.
        catalogo.asegurarArticulo("AMI", "ULL911.AL0100");

        assertTrue(memoria.buscar("AMI", "ULL911.AL0100").isEmpty());
    }

    // --- Sin artículo en el catálogo no se guarda nada (decisión del usuario) ---

    @Test
    void sinModeloEnElCatalogoNoSeRecuerdaLaCajaNiSeDaDeAlta() {
        memoria.recordarCaja("AMI", "ULL920.AL0100", "60x40x40", 6);

        assertTrue(catalogo.articulo("AMI", "ULL920.AL0100").isEmpty(),
                "el packing no da de alta modelos en el catálogo");
        assertTrue(catalogo.modelo("AMI", "ULL920").isEmpty());
    }

    @Test
    void conElModeloPeroSinEsaPielTampocoSeGuarda() {
        catalogo.asegurarArticulo("AMI", "ULL921.AL0100");

        boolean guardado = memoria.recordarPesoUnidad("AMI", "ULL921.AL0200", 0.5);

        assertFalse(guardado);
        assertTrue(catalogo.articulo("AMI", "ULL921.AL0200").isEmpty());
    }

    // --- El peso es por unidad y va aparte del cartón ---

    @Test
    void elPesoPorUnidadSeGuardaYSeRecupera() {
        catalogo.asegurarArticulo("AMI", "ULL930.AL0100");

        assertTrue(memoria.recordarPesoUnidad("AMI", "ULL930.AL0100", 0.45));

        assertEquals(0.45, memoria.pesoNetoUnidad("AMI", "ULL930.AL0100").orElseThrow());
        assertEquals(0.45, catalogo.articulo("AMI", "ULL930.AL0100").orElseThrow().getPesoNetoUnidadKg(),
                "vive en el artículo del catálogo");
    }

    @Test
    void elPesoPorUnidadSeGuardaConCuatroDecimales() {
        catalogo.asegurarArticulo("AMI", "ULL931.AL0100");

        memoria.recordarPesoUnidad("AMI", "ULL931.AL0100", 1.0 / 3);

        assertEquals(0.3333, memoria.pesoNetoUnidad("AMI", "ULL931.AL0100").orElseThrow());
    }

    @Test
    void elPesoSeGuardaAunqueNoSeSepaElCarton() {
        // La revisión sabe lo que pesa una caja pero casi nunca cuántas
        // unidades caben: por eso el peso es por unidad.
        catalogo.asegurarArticulo("AMI", "ULL932.AL0100");

        memoria.recordarPesoUnidad("AMI", "ULL932.AL0100", 0.2);

        assertEquals(0.2, memoria.pesoNetoUnidad("AMI", "ULL932.AL0100").orElseThrow());
        assertTrue(memoria.buscar("AMI", "ULL932.AL0100").isEmpty());
    }

    @Test
    void recordarLaCajaNoBorraElPesoQueYaHabia() {
        catalogo.asegurarArticulo("AMI", "ULL933.AL0100");
        memoria.recordarPesoUnidad("AMI", "ULL933.AL0100", 0.5);

        memoria.recordarCaja("AMI", "ULL933.AL0100", "60x40x30", 6);

        assertEquals(0.5, memoria.pesoNetoUnidad("AMI", "ULL933.AL0100").orElseThrow());
    }

    @Test
    void unPesoQueNoSeaPositivoNoBorraElQueHabia() {
        // Pesar una caja cuesta bajarla a la báscula: un envío sin pesar no
        // tira el trabajo de alguien.
        catalogo.asegurarArticulo("AMI", "ULL934.AL0100");
        memoria.recordarPesoUnidad("AMI", "ULL934.AL0100", 0.5);

        assertFalse(memoria.recordarPesoUnidad("AMI", "ULL934.AL0100", 0.0));
        assertFalse(memoria.recordarPesoUnidad("AMI", "ULL934.AL0100", null));

        assertEquals(0.5, memoria.pesoNetoUnidad("AMI", "ULL934.AL0100").orElseThrow());
    }
}

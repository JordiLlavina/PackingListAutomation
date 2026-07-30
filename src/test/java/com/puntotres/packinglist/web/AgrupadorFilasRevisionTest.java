package com.puntotres.packinglist.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.CajaData;

/**
 * Compactación de la tabla de revisión: cajas consecutivas equivalentes se
 * pintan en una sola fila con el rango de números de caja.
 *
 * La regla que anclan estos tests: una fila compactada NUNCA muestra un valor
 * que no sea cierto para todas sus cajas. Cualquier diferencia —palet, peso,
 * tamaño, talla— parte el grupo en vez de esconderse bajo el rango.
 */
class AgrupadorFilasRevisionTest {

    /** Caja de una línea con todo lo que entra en la clave de agrupación. */
    private static CajaData caja(int numero, String referencia, String color, String pedido,
                                 int cantidad, String tamano, Integer palet,
                                 Double neto, Double bruto) {
        CajaData caja = new CajaData();
        caja.setNumeroCaja(numero);
        caja.setReferencia(referencia);
        caja.setCodigoColor(color);
        caja.setNumeroPedido(pedido);
        caja.setCantidad(cantidad);
        caja.setTamanoCaja(tamano);
        caja.setNumeroPalet(palet);
        caja.setPesoNetoKg(neto);
        caja.setPesoBrutoKg(bruto);
        return caja;
    }

    /** Cinco cajas idénticas y correlativas, de la 4 a la 8. */
    private static List<CajaData> cajas4a8() {
        return List.of(
                caja(4, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6),
                caja(5, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6),
                caja(6, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6),
                caja(7, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6),
                caja(8, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6));
    }

    @Test
    void cincoCajasIgualesYCorrelativasSeCompactanEnUnaSolaFilaConSuRango() {
        List<FilaCaja> filas = AgrupadorFilasRevision.agrupar(cajas4a8(), 0);

        assertEquals(1, filas.size());
        FilaCaja fila = filas.get(0);
        assertEquals("4-8", fila.rangoCajas());
        // La cantidad es la de UNA caja, no la suma del grupo.
        assertEquals(5, fila.caja().getCantidad());
        // El peso tecleado en esta fila tiene que llegar a las cinco cajas.
        assertEquals(List.of(0, 1, 2, 3, 4), fila.indicesEnDestino());
        assertTrue(fila.esLider());
    }

    @Test
    void unaCajaSueltaMuestraSuNumeroSinGuion() {
        List<FilaCaja> filas = AgrupadorFilasRevision.agrupar(
                List.of(caja(4, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6)), 0);

        assertEquals(1, filas.size());
        assertEquals("4", filas.get(0).rangoCajas());
        assertEquals(List.of(0), filas.get(0).indicesEnDestino());
    }

    @Test
    void unCambioDePaletParteElGrupo() {
        List<CajaData> cajas = cajas4a8();
        cajas.get(2).setNumeroPalet(2);   // la caja 6 se va a otro palet

        List<FilaCaja> filas = AgrupadorFilasRevision.agrupar(cajas, 0);

        assertEquals(List.of("4-5", "6", "7-8"),
                filas.stream().map(FilaCaja::rangoCajas).toList());
        assertEquals(1, filas.get(0).caja().getNumeroPalet());
        assertEquals(2, filas.get(1).caja().getNumeroPalet());
        assertEquals(1, filas.get(2).caja().getNumeroPalet());
    }

    @Test
    void unPesoDistintoParteElGrupo() {
        List<CajaData> cajas = cajas4a8();
        cajas.get(4).setPesoBrutoKg(9.9);   // la caja 8 se pesó a mano

        List<FilaCaja> filas = AgrupadorFilasRevision.agrupar(cajas, 0);

        assertEquals(List.of("4-7", "8"),
                filas.stream().map(FilaCaja::rangoCajas).toList());
    }

    @Test
    void unTamanoDeCajaDistintoParteElGrupo() {
        List<CajaData> cajas = cajas4a8();
        cajas.get(3).setTamanoCaja("60x40x30");

        List<FilaCaja> filas = AgrupadorFilasRevision.agrupar(cajas, 0);

        assertEquals(List.of("4-6", "7", "8"),
                filas.stream().map(FilaCaja::rangoCajas).toList());
    }

    @Test
    void unaTallaDistintaParteElGrupoAunqueNoSeaColumnaVisible() {
        // Cinturones: misma referencia, color, PO y cantidad, distinta talla.
        // Esconderlas bajo un "4-5" borraría una diferencia que sí importa
        // aguas abajo (la etiqueta usa la talla de la línea líder).
        CajaData talla75 = caja(4, "UBL029.AL0216", "001", "07672", 45, "60x40x40", 1, 5.0, 5.6);
        talla75.setTalla("75");
        CajaData talla85 = caja(5, "UBL029.AL0216", "001", "07672", 45, "60x40x40", 1, 5.0, 5.6);
        talla85.setTalla("85");

        List<FilaCaja> filas = AgrupadorFilasRevision.agrupar(List.of(talla75, talla85), 0);

        assertEquals(List.of("4", "5"),
                filas.stream().map(FilaCaja::rangoCajas).toList());
    }

    @Test
    void unNumeroDeCajaNoCorrelativoParteElGrupo() {
        // Las cajas 4, 5 y 9 son idénticas, pero un rango "4-9" prometería
        // seis cajas y solo representaría tres.
        List<CajaData> cajas = List.of(
                caja(4, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6),
                caja(5, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6),
                caja(9, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6));

        List<FilaCaja> filas = AgrupadorFilasRevision.agrupar(cajas, 0);

        assertEquals(List.of("4-5", "9"),
                filas.stream().map(FilaCaja::rangoCajas).toList());
    }

    @Test
    void unaCajaMixtaNoSeCompactaYSoloSuPrimeraLineaEsLider() {
        // La caja 9 tiene dos líneas (dos referencias en el mismo bulto): no
        // entra en la compactación y solo su primera línea lleva pesos.
        List<CajaData> cajas = List.of(
                caja(9, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6),
                caja(9, "UBL010", "NOIR", "PO123", 2, "60x40x40", 1, null, null),
                caja(10, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6));

        List<FilaCaja> filas = AgrupadorFilasRevision.agrupar(cajas, 0);

        assertEquals(3, filas.size());
        assertEquals(List.of("9", "9", "10"),
                filas.stream().map(FilaCaja::rangoCajas).toList());
        assertTrue(filas.get(0).esLider());
        assertFalse(filas.get(1).esLider());
        assertTrue(filas.get(2).esLider());
    }

    @Test
    void unaCajaMixtaNoSeFusionaConLaCajaDeUnaLineaQueLaSigue() {
        // Regresión del criterio "solo cajas de una línea": la segunda línea
        // de la caja 9 y la caja 10 coinciden en todo, pero la 9 es mixta.
        List<CajaData> cajas = List.of(
                caja(9, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6),
                caja(9, "UBL010", "NOIR", "PO123", 2, "60x40x40", 1, 5.0, 5.6),
                caja(10, "UBL010", "NOIR", "PO123", 2, "60x40x40", 1, 5.0, 5.6));

        List<FilaCaja> filas = AgrupadorFilasRevision.agrupar(cajas, 0);

        assertEquals(List.of("9", "9", "10"),
                filas.stream().map(FilaCaja::rangoCajas).toList());
    }

    @Test
    void unaCajaSinPesosCompletosQuedaMarcadaComoPendiente() {
        List<CajaData> cajas = List.of(
                caja(4, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, null, null),
                caja(5, "ULL163", "NOIR", "PO123", 5, "60x40x40", 1, 5.0, 5.6));

        List<FilaCaja> filas = AgrupadorFilasRevision.agrupar(cajas, 0);

        assertEquals(2, filas.size());
        assertTrue(filas.get(0).cajaPendiente());
        assertFalse(filas.get(1).cajaPendiente());
    }

    @Test
    void losIndicesGlobalesArrancanDondeSeLesDiceYNoSeRepiten() {
        // El índice global nombra los inputs del formulario y es único en toda
        // la página: la segunda destinación sigue donde acabó la primera.
        List<FilaCaja> filas = AgrupadorFilasRevision.agrupar(
                List.of(caja(1, "ULL163", "NOIR", "PO1", 5, "60x40x40", 1, 5.0, 5.6),
                        caja(2, "ULL200", "NOIR", "PO1", 5, "60x40x40", 1, 4.0, 4.6)),
                7);

        assertEquals(List.of(7, 8), filas.stream().map(FilaCaja::indiceGlobal).toList());
    }
}

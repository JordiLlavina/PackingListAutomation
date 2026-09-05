package com.puntotres.packinglist.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * La altura de la caja decide cuántas caben en una pila y, con ello, cuántos
 * palets lleva el envío. Que sea la última dimensión y no la de en medio no
 * es un detalle: lo dice el catálogo de taras, donde los cartones comparten
 * suelo (60x40) y se diferencian en el tercer número.
 */
class MedidaCajaTest {

    @Test
    void laAlturaEsLaUltimaDimension() {
        MedidaCaja medida = MedidaCaja.parse("60x40x45").orElseThrow();

        assertEquals(60, medida.largo());
        assertEquals(40, medida.ancho());
        assertEquals(45, medida.alto());
    }

    @Test
    void seNormalizaComoLasClavesDeLaTablaDeTaras() {
        assertEquals("60x40x45", MedidaCaja.parse(" 60 X 40 x 45 ").orElseThrow().normalizada());
    }

    @Test
    void unaMedidaIlegibleNoSeAdivina() {
        assertTrue(MedidaCaja.parse("60x40").isEmpty(), "faltan dimensiones");
        assertTrue(MedidaCaja.parse("60x40x40x40").isEmpty(), "sobran dimensiones");
        assertTrue(MedidaCaja.parse("grande").isEmpty());
        assertTrue(MedidaCaja.parse("60x0x40").isEmpty(), "una caja de altura cero no existe");
        assertTrue(MedidaCaja.parse("").isEmpty());
        assertTrue(MedidaCaja.parse(null).isEmpty());
    }

    @Test
    void elVolumenOrdenaCajasQueElAlfabetoOrdenariaAlReves() {
        long grande = MedidaCaja.parse("100x40x40").orElseThrow().volumen();
        long pequena = MedidaCaja.parse("40x30x20").orElseThrow().volumen();

        assertTrue(grande > pequena);
    }
}

package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FotosTemporadaTest {

    @Test
    void laPrincipalEsLaQueSeLlamaPrincipalMainOCeroConCualquierExtension() {
        assertEquals(1, indice("0001.jpg", "0.png", "detalle.jpg"));
        assertEquals(2, indice("0001.jpg", "detalle.jpg", "MAIN.HEIC"));
        assertEquals(0, indice("Principal.jpeg", "detalle.jpg"));
    }

    @Test
    void conVariasGanaPrincipalLuegoMainLuegoCero() {
        assertEquals(2, indice("0.jpg", "main.png", "principal.heic"));
        assertEquals(1, indice("0.jpg", "main.png", "zz.jpg"));
    }

    @Test
    void unNombreQueSoloLoContieneNoCuenta() {
        // «main-lateral» o «10» no son la principal: el nombre tiene que ser ese.
        assertEquals(0, indice("a.jpg", "main-lateral.jpg", "10.jpg", "principal2.png"));
    }

    @Test
    void sinNingunaConEsosNombresEsLaPrimeraPorNombreComoSiempre() {
        assertEquals(0, indice("0001.jpg", "detalle.jpg"));
        assertEquals(0, FotosTemporada.vacia().indicePorDefecto("ULL027"));
    }

    private static int indice(String... nombres) {
        List<FotoModelo> fotos = Arrays.stream(nombres)
                .map(nombre -> new FotoModelo("ULL027", nombre, Path.of(nombre)))
                .toList();
        return new FotosTemporada(Map.of("ULL027", fotos), List.of()).indicePorDefecto("ULL027");
    }
}

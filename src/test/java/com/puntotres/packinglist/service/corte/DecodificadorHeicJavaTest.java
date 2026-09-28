package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.testutil.FotosDePrueba;

class DecodificadorHeicJavaTest {

    @Test
    void decodificaUnHeicDeVerdad(@TempDir Path dir) throws Exception {
        Path heic = dir.resolve("a.heic");
        Files.write(heic, FotosDePrueba.heicDePrueba());

        BufferedImage imagen = new DecodificadorHeicJava().decodificar(heic);

        assertEquals(430, imagen.getWidth());
        assertEquals(430, imagen.getHeight());
    }

    @Test
    void conUnPresupuestoMinimoLaFotoSaleIgualAunqueVayaSola(@TempDir Path dir) throws Exception {
        Path heic = dir.resolve("a.heic");
        Files.write(heic, FotosDePrueba.heicDePrueba());

        assertEquals(430, new DecodificadorHeicJava(1).decodificar(heic).getWidth());
    }

    @Test
    void loQueNoEsUnHeicEsUnaIOException(@TempDir Path dir) throws IOException {
        Path falso = dir.resolve("falso.heic");
        Files.writeString(falso, "no soy un heic");

        assertThrows(IOException.class, () -> new DecodificadorHeicJava().decodificar(falso));
    }
}
